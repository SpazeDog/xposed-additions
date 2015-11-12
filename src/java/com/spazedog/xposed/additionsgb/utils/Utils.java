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

package com.spazedog.xposed.additionsgb.utils;

import android.content.Context;
import android.util.Log;

import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;

import java.io.File;

public final class Utils {
	
	private static int oDebugFlags = -2;
    private static int oUserId = -1;
	
	public enum Level {INFO, DEBUG, WARNING, ERROR}
    public class Type {
        public final static int DISABLED = -1;
        public final static int EXTENDED = 0x00000001;

        public final static int BUTTONS = 0x00000002;
        public final static int POWER = 0x00000004;
        public final static int SERVICES = 0x00000008;
        public final static int LAYOUT = 0x00000010;
        public final static int STATE = 0x00000020;

        public final static int ALL = EXTENDED|BUTTONS|POWER|SERVICES|LAYOUT|STATE;
    }
	
	public static void log(Level level, String tag, String message) {
		log(0, level, tag, message, null);
	}

    public static void log(int type, Level level, String tag, String message) {
        log(type, level, tag, message, null);
    }

    public static void log(Level level, String tag, String message, Throwable e) {
        log(0, level, tag, message, e);
    }
	
	public static void log(int type, Level level, String tag, String message, Throwable e) {
		if (level == Level.DEBUG) {
            if (!isDebugEnabled()) {
                /*
                 * If we have a DEBUG log before the service is ready,
                 * transform it into an INFO log instead.
                 */
                if (oDebugFlags != -2) {
                    return;
                }

                level = Level.INFO;

            } else if ((getDebugFlags() & type) != type) {
                return;
            }
		}
		
		switch (level) {
			case DEBUG: Log.d(tag, message, e); break;
			case WARNING: Log.w(tag, message, e); break;
			case ERROR: Log.e(tag, message, e); break;
			default: Log.i(tag, message, e);
		}
	}

    public static boolean isDebugEnabled() {
        return getDebugFlags() >= 0;
    }
	
	public static int getDebugFlags() {
		/*
		 * We do not want to check this value against the service every time. 
		 * Only do it until the service is ready and we can get the preference for debug settings. 
		 */
		if (oDebugFlags == -2) {
			if (!Constants.FORCE_DEBUG) {
                /*
                 * Avoid recursive calls when we start calling the service
                 */
                oDebugFlags = -1;

				BackendServiceMgr backenedMgr = BackendServiceMgr.getInstance(true);

				if (backenedMgr != null && backenedMgr.isServiceReady()) {
                    oDebugFlags = backenedMgr.getDebugFlags();

				} else {
                    oDebugFlags = -2;
				}

			} else {
                oDebugFlags = Type.ALL;
			}
		}
		
		return oDebugFlags;
	}

    public static boolean isOwner(Context context) {
        if (oUserId == -1) {
            try {
                /*
                 * /data/data/<package> on version without multi user support, always the owner
                 * /data/user/<userId>/<package> on newer Android versions, we want <userId>
                 */
                oUserId = Integer.valueOf(new File(context.getApplicationInfo().dataDir).getParentFile().getName());

            } catch (NumberFormatException e) {
                /*
                 * We hit an older Android version, owner is all we have
                 */
                oUserId = 0;
            }
        }

        return oUserId == 0;
    }

    public static ClassLoader getAppClassLoader() {
        return Utils.class.getClassLoader();
    }
}
