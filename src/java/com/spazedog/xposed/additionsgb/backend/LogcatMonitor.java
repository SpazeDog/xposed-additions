/*
 * This file is part of the Xposed Additions Project: https://github.com/spazedog/xposed-additions
 *  
 * Copyright (c) 2015 Daniel Bergløv
 *
 * Xposed Additions is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Xposed Additions is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Xposed Additions. If not, see <http://www.gnu.org/licenses/>
 */

package com.spazedog.xposed.additionsgb.backend;

import android.annotation.SuppressLint;
import android.os.*;
import android.os.Process;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("UseSparseArrays")
public class LogcatMonitor {
	public static final String TAG = LogcatMonitor.class.getName();

	private final static int DEBUG = 3;
	private final static int INFO = 4;
	private final static int ERROR = 6;

    private static volatile String[] oLogEntries = new String[0];
	private final static Map<Integer, String> LEVELS = new HashMap<Integer, String>();
	static {
		LEVELS.put(DEBUG, "D");
		LEVELS.put(INFO, "I");
		LEVELS.put(ERROR, "E");
	}
	
	public final Object mLock = new Object();
	public volatile boolean mBusy = false;
	
	public static void init() {
		Utils.log(Level.INFO, TAG, "Instantiating Logcat Monitor");
		
		try {
			ReflectClass.fromName("android.util.Log")
				.bridge("println_native", new LogcatMonitor().hook_println_native);
			
		} catch (ReflectException e) {
			Utils.log(Level.ERROR, TAG, e.getMessage(), e);
		}
	}

    public static List<String> getLogEntries() {
        return getLogEntries(false);
    }

    public static List<String> getLogEntries(boolean clearLog) {
        synchronized (oLogEntries) {
            List<String> logsEntries = new ArrayList<String>();

            for (String entry : oLogEntries) {
                logsEntries.add(entry);
            }

            if (clearLog) {
                oLogEntries = new String[0];
            }

            return logsEntries;
        }
    }
	
	/**
	 * The restrictions in Lollipop+ makes it almost impossible to write logs to a file. 
	 * Since we need logs as early as possible, using bound services is not an option. 
	 * Also adding entries to the module service is much better than files. 
	 * It is faster and it makes truncating much more optimized. 
	 */
	protected MethodBridge hook_println_native = new MethodBridge() {
		@Override
		public void bridgeBegin(BridgeParams param) {
			synchronized (mLock) {
				String message = (String) param.args[3];
				String tag = (String) param.args[2];
				int priority = (Integer) param.args[1];
				boolean tagHasName = tag != null && tag.contains(Constants.PACKAGE_NAME);
				
				if (tagHasName || (priority == ERROR && message.contains(Constants.PACKAGE_NAME))) {
					if (!mBusy) {
						mBusy = true;
						
						String entry = "";
						entry += LEVELS.get(priority);
						entry += "/";
						entry += tag;
						entry += "\r\n\t";
						entry += message.replace("\n", "\r\n\t\t");
						entry += "\r\n";
						
						BackendServiceMgr manager = BackendServiceMgr.getInstance(true);
						
						if (manager != null && manager.isServiceActive()) {
							manager.sendListenerMsg(Constants.BRC_LOGCAT, new HashBundle("entry", entry));

						} else if (Binder.getCallingUid() <= Process.SYSTEM_UID) {
                            /*
                             * TODO:
                             *      This is not working properly. Depending on Android version, it differs which processes will inherit the data.
                             *      It does however assemble the system process log, but we need a better way so that we are able to display the log data
                             *      in the apps log viewer if the service is not started for some reason.
                             */
                            synchronized (oLogEntries) {
                                if (oLogEntries.length < Constants.LOG_ENTRY_SIZE) {
                                    String[] logEntries = new String[ oLogEntries.length+1 ];
                                    logEntries[oLogEntries.length] = entry;

                                    for (int i=0; i < oLogEntries.length; i++) {
                                        logEntries[i] = oLogEntries[i];
                                    }

                                    oLogEntries = logEntries;
                                }
                            }
						}
						
						mBusy = false;
					}
				}
			}
		}
	};
}
