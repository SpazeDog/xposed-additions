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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class LogcatMonitor {
	public static final String TAG = LogcatMonitor.class.getName();

    public static List<LogcatEntry> buildLog() {
        List<LogcatEntry> ret = new SparseList<LogcatEntry>();

        try {
            String[] command = new String[] { "logcat", "-v", "tag", "-d" };
            java.lang.Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line, log = "";
            boolean group = false;
            boolean child;
            int i=0;

            while ((line = reader.readLine()) != null) {
                /*
                 * <TYPE>/<TAG>: <LOG>
                 *
                 * We need to get to the <LOG> part.
                 * <TAG> some time has space, so we need to account for this.
                 * This will work in 99.9% of all cases and I have never seen the 0.1%
                 */
                int cpos = line.indexOf(":");
                int spos = line.indexOf(" ", cpos)+1;

                if (spos == line.length()) {
                    continue;
                }

                child = Character.isWhitespace(line.charAt(spos));

                if (!child) {
                    i++;

                    /*
                     * We do not know where the log starts, as it get's truncated when reaching a specific size.
                     * It might now begin with a complete log entry. This is to ensure that we skip
                     * incomplete entries. Also it is used to skip all entries that is not errors.
                     */
                    group = true;

                    /*
                     * We can only read a line at a time.
                     * Just because the headline does not contain this package name,
                     * does not mean that the log does not relate to it.
                     * We need to collect the whole part of each log before we can decide.
                     */
                    if (!log.isEmpty() && log.contains(Constants.PACKAGE_NAME)) {
                        if (ret.size() > Constants.LOG_ENTRY_SIZE) {
                            ret.remove(0);
                        }

                        ret.add(new LogcatEntry(log));
                    }

                    if (group) {
                        log = line;
                    }

                } else if (group) {
                    log += "\r\n\t\t";
                    log += line.substring(spos).trim();
                }
            }

            if (!log.isEmpty() && log.contains(Constants.PACKAGE_NAME)) {
                ret.add(new LogcatEntry(log));
            }

        } catch (Throwable e) {
            Log.e(TAG, e.getMessage(), e);
        }

        return ret;
    }

    @SuppressLint("ParcelCreator")
    public static class LogcatEntry extends MultiParcelableBuilder {

        public final long Time;
        public final int Pid;
        public final int Uid;
        public final int Level;
        public final String Tag;
        public final String Message;

        public LogcatEntry(String entry) {
            char type = entry.charAt(0);

            switch (type) {
                case 'D': Level = Log.DEBUG; break;
                case 'W': Level = Log.WARN; break;
                case 'E': Level = Log.ERROR; break;
                default: Level = Log.INFO;
            }

            int cpos = entry.indexOf(":");
            int spos = entry.indexOf(" ", cpos);

            String tag = entry.substring(2, spos-1);

            Tag = tag.startsWith(Constants.PACKAGE_NAME) ? ("XposedAdditions: " + tag.substring(tag.lastIndexOf(".")+1).trim()) : tag.trim();
            Message = entry.substring(spos+1);
            Time = System.currentTimeMillis();
            Pid = 0;
            Uid = 0;
        }

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
                    }

                    mBusy = false;
                }
            }
        }
    };
}
