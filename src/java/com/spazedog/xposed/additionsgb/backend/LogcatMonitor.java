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

import android.os.Parcel;
import android.os.Process;
import android.util.Log;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.MultiParcelableBuilder;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

import java.util.List;

public class LogcatMonitor {
	public static final String TAG = LogcatMonitor.class.getName();

    private static List<LogcatEntry> oLogEntries = new SparseList<LogcatEntry>();

    public static class LogcatEntry extends MultiParcelableBuilder {

        public final long Time;
        public final int Pid;
        public final int Uid;
        public final int Level;
        public final String Tag;
        public final String Message;

        public LogcatEntry(int pid, int uid, int level, String tag, String message) {
            Time = System.currentTimeMillis();
            Pid = pid;
            Uid = uid;
            Level = level;
            Tag = tag.startsWith(Constants.PACKAGE_NAME) ? ("XposedAdditions: " + tag.substring(tag.lastIndexOf(".")+1).trim()) : tag.trim();
            Message = message.replace("\n", "\r\n\t\t").trim();
        }

        public LogcatEntry(Parcel in, ClassLoader loader) {
            Time = (Long) unparcelData(in, loader);
            Pid = (Integer) unparcelData(in, loader);
            Uid = (Integer) unparcelData(in, loader);
            Level = (Integer) unparcelData(in, loader);
            Tag = (String) unparcelData(in, loader);
            Message = (String) unparcelData(in, loader);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);

            parcelData(Time, out, flags);
            parcelData(Pid, out, flags);
            parcelData(Uid, out, flags);
            parcelData(Level, out, flags);
            parcelData(Tag, out, flags);
            parcelData(Message, out, flags);
        }
    }
	
	public final Object mLock = new Object();
	public volatile boolean mBusy = false;
	
	public static void init() {
		Utils.log(Level.INFO, TAG, "Instantiating Logcat Monitor");
		
		try {
            LogcatMonitor instance = new LogcatMonitor();
            ReflectClass logcat = ReflectClass.fromName("android.util.Log");

            logcat.bridge("println_native", instance.hook_println_native);
			
		} catch (ReflectException e) {
			Utils.log(Level.ERROR, TAG, e.getMessage(), e);
		}
	}

    public static List<LogcatEntry> getLogEntries() {
        return getLogEntries(false);
    }

    public static List<LogcatEntry> getLogEntries(boolean clearLog) {
        synchronized (oLogEntries) {
            List<LogcatEntry> logsEntries = new SparseList<LogcatEntry>();

            for (LogcatEntry entry : oLogEntries) {
                logsEntries.add(entry);
            }

            if (clearLog) {
                oLogEntries.clear();
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
                int priority = (Integer) param.args[1];
                String tag = (String) param.args[2];
                String message = (String) param.args[3];

                if (!mBusy && (tag != null && tag.contains(Constants.PACKAGE_NAME)) || (priority == Log.ERROR && message.contains(Constants.PACKAGE_NAME))) {
                    mBusy = true;

                    BackendServiceMgr manager = BackendServiceMgr.getInstance(true);

                    if (manager != null && manager.isServiceActive()) {
                        manager.sendListenerMsg(Constants.BRC_LOGCAT, new HashBundle("entry", new LogcatEntry(Process.myPid(), Process.myUid(), priority, tag, message)));

                    } else if (Process.myUid() <= Process.SYSTEM_UID) {
                        /*
                         * TODO:
                         *      This is not working properly. Depending on Android version, it differs which processes will inherit the data.
                         *      It does however assemble the system process log, but we need a better way so that we are able to display the log data
                         *      in the apps log viewer if the service is not started for some reason.
                         */
                        synchronized (oLogEntries) {
                            if (oLogEntries.size() < Constants.LOG_ENTRY_SIZE) {
                                oLogEntries.add(new LogcatEntry(Process.myPid(), Process.myUid(), priority, tag, message));
                            }
                        }
                    }

                    mBusy = false;
                }
            }
        }
    };
}
