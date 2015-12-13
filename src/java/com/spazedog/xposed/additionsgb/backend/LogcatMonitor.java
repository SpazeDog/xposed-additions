/*
 * This file is part of the Xposed Additions Project: https://github.com/spazedog/xposed-additions
 *
 * Copyright (c) 2014 Daniel Bergløv
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


import android.os.Bundle;
import android.util.Log;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class LogcatMonitor {
    public static final String TAG = LogcatMonitor.class.getName();

    public static List<String> buildLog() {
        List<String> ret = new ArrayList<String>();

        try {
            String[] command = new String[] { "logcat", "-v", "tag", "-d" };
            Process process = Runtime.getRuntime().exec(command);
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
                    group = line.startsWith("E");

                    /*
                     * We can only read a line at a time.
                     * Just because the headline does not contain this package name,
                     * does not mean that the log does not relate to it.
                     * We need to collect the whole part of each log before we can decide.
                     */
                    if (!log.isEmpty() && log.contains(Common.PACKAGE_NAME)) {
                        ret.add(log);
                    }

                    if (group) {
                        log = line;
                    }

                } else if (group) {
                    log += "\r\n\t\t";
                    log += line.substring(spos).trim();
                }
            }

            if (!log.isEmpty() && log.contains(Common.PACKAGE_NAME)) {
                ret.add(log);
            }

        } catch (Throwable e) {
            Log.e(TAG, e.getMessage(), e);
        }

        return ret;
    }

    public final Object mLock = new Object();
    public volatile boolean mBusy = false;

    public static void init() {
        Log.i(TAG, "Adding Logcat Monitor Hook");

        try {
            LogcatMonitor instance = new LogcatMonitor();
            ReflectClass logcat = ReflectClass.fromName("android.util.Log");

            logcat.bridge("println_native", instance.hook_println_native);

        } catch (ReflectException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    protected MethodBridge hook_println_native = new MethodBridge() {
        @Override
        public void bridgeBegin(BridgeParams param) {
            synchronized (mLock) {
                int priority = (Integer) param.args[1];
                String tag = (String) param.args[2];
                String message = (String) param.args[3];

                if (!mBusy && tag != null && priority == Log.ERROR && (tag.contains(Common.PACKAGE_NAME) || message.contains(Common.PACKAGE_NAME))) {
                    mBusy = true;

                    XServiceManager manager = XServiceManager.getInstance(true);

                    if (manager != null && manager.isServiceActive()) {
                        String[] line = message.trim().split("\n");
                        String log = "E/";
                        log += tag;

                        for (int i=0; i < line.length; i++) {
                            if (i == 0) {
                                log += line[i];

                            } else {
                                log += "\r\n\t\t";
                                log += line[i].trim();
                            }
                        }

                        Bundle data = new Bundle();
                        data.putString("log", log);

                        manager.sendBroadcast("logcat.entry", data);
                    }
                }
            }
        }
    };
}
