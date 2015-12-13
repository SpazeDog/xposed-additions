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

import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.xposed.additionsgb.Common;

import java.lang.reflect.Field;

public class InputManager {
	public static final String TAG = InputManager.class.getName();

    public static int FLAG_INJECTED = 0x40000000;

    protected static Field mFieldKeyFlags;

    static {
        try {
            mFieldKeyFlags = KeyEvent.class.getDeclaredField("mFlags");

        } catch (Throwable e) {
        }
    }
	
	public static void init() {
		if(Common.DEBUG) Log.d(TAG, "Adding Input Manager Hook");

        InputManager hook = new InputManager();

        try {
            // Gingerbread
            ReflectClass.fromName("com.android.server.InputManager").bridge("nativeInjectInputEvent", hook.hook_injectInputEvent);

        } catch (ReflectException e) {
            try {
                // ICS
                ReflectClass.fromName("com.android.server.wm.InputManager").bridge("nativeInjectInputEvent", hook.hook_injectInputEvent);

            } catch (ReflectException e2) {
                try {
                    // Jellybean+
                    ReflectClass.fromName("com.android.server.input.InputManagerService").bridge("nativeInjectInputEvent", hook.hook_injectInputEvent);

                } catch (ReflectException e3) {
                    Log.e(TAG, e3.getMessage(), e3);
                }
            }
        }
	}

	protected MethodBridge hook_injectInputEvent = new MethodBridge() {
		@Override
        public void bridgeEnd(BridgeParams params) {
            InputEvent event = (InputEvent) (params.args[1] instanceof InputEvent ? params.args[1] : params.args[0]);

			if (params.args[0] instanceof KeyEvent) {
                KeyEvent keyEvent = (KeyEvent) event;
                int keyFlags = keyEvent.getFlags();

                /*
                 * KitKat has an error where PolicyFlags[FLAG_INJECTED] will always show the key as injected in PhoneWindowManager#interceptKeyBeforeDispatching.
                 * Since our PhoneWindowManager hook depends on being able to distinguish between button presses
                 * and actual injected keys, we have added this small hook that will add the FLAG_INJECTED flag directly to the
                 * KeyEvent itself whenever it get's parsed though this service method.
                 */
                if ((keyFlags & FLAG_INJECTED) != FLAG_INJECTED) {
                    try {
                        mFieldKeyFlags.set(keyEvent, keyFlags | FLAG_INJECTED);

                    } catch (Throwable e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
			}
		}
	};
}
