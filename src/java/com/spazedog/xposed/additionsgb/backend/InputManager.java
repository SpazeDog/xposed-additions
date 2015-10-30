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

import android.view.InputEvent;
import android.view.KeyEvent;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

import java.lang.reflect.Field;

/*
 * Some changes made to the native Input Manager in KitKat introduced an error in PhoneWindowManager#interceptKeyBeforeDispatching().
 * For some reason the policy flags always contains the FLAG_INJECTED. Since Android only uses this flag in PhoneWindowManager#interceptKeyBeforeQueueing()
 * this issue has stayed in all Lollipop releases as well and will possibly stay in even further releases.
 *
 * XposedAdditions adds much more advanced key features and needs to know the key type in both these methods.
 * But since we cannot affect native code, our only option is to add a similar flag directly to a key before injecting it in order to have it persist across it's life time.
 * Again since we can only affect the Java part of the framework, this will not work on keys injected via native code like Double Tab Screen features, which is mostly invoked
 * by the kernel itself via native input tools.
 *
 * TODO: Find a solution to the above issue
 */
public class InputManager {
    public static final String TAG = InputManager.class.getName();

    /*
     * Custom key flag values. We use the last two bits in a
     * 32bit integer so not to conflict with Android's flag values
     */
    public static final int KEYFLAG_INTERNAL = 0x80000000;
    public static final int KEYFLAG_INJECTED = 0x40000000;

    /*
     * From android.view.WindowManagerPolicy
     */
    public final static int POLICYFLAG_TRUSTED = 0x02000000;
    public final static int POLICYFLAG_INJECTED = 0x01000000;
    public final static int POLICYFLAG_VIRTUAL = 0x00000002;
    public final static int POLICYFLAG_INTERACTIVE = 0x20000000;

    /*
     * Jellybean+
     *
     * From android.hardware.input.InputManager
     */
    public static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;

    /*
     * Field that will allow us to alter key flags on KeyEvent objects
     */
    protected static Field mFieldKeyFlags;

    static {
        try {
            mFieldKeyFlags = KeyEvent.class.getDeclaredField("mFlags");

        } catch (Throwable e) {
        }
    }

    public static void init() {
        Utils.log(Level.INFO, TAG, "Instantiating InputManager");

        InputManager instance = new InputManager();

        try {
            /* Gingerbread
             *
             * com.android.server.InputManager$nativeInjectInputEvent(InputEvent event, int injectorPid, int injectorUid, int syncMode, int timeoutMillis)
             */
            ReflectClass.fromName("com.android.server.InputManager").bridge("nativeInjectInputEvent", instance.injectInputEvent);

        } catch (ReflectException e) {
            try {
                /* ICS
                 *
                 * com.android.server.wm.InputManager$nativeInjectInputEvent(InputEvent event, int injectorPid, int injectorUid, int syncMode, int timeoutMillis, int policyFlags)
                 */
                ReflectClass.fromName("com.android.server.wm.InputManager").bridge("nativeInjectInputEvent", instance.injectInputEvent);

            } catch (ReflectException e2) {
                try {
                    /* Jellybean+
                     *
                     * com.android.server.input.InputManager$nativeInjectInputEvent(int ptr, InputEvent event, int injectorPid, int injectorUid, int syncMode, int timeoutMillis, int policyFlags)
                     */
                    ReflectClass.fromName("com.android.server.input.InputManagerService").bridge("nativeInjectInputEvent", instance.injectInputEvent);

                } catch (ReflectException e3) {
                    Utils.log(Level.ERROR, TAG, e3.getMessage(), e3);
                }
            }
        }
    }

    public MethodBridge injectInputEvent = new MethodBridge() {
        @Override
        public void bridgeBegin(BridgeParams params) {
            InputEvent event = (InputEvent) (params.args[1] instanceof InputEvent ? params.args[1] : params.args[0]);

            if (event instanceof KeyEvent) {
                KeyEvent keyEvent = (KeyEvent) event;
                int keyFlags = keyEvent.getFlags();

                if ((keyFlags & KEYFLAG_INJECTED) != KEYFLAG_INJECTED) {
                    try {
                        mFieldKeyFlags.set(keyEvent, keyFlags|KEYFLAG_INJECTED);

                    } catch (Throwable e) {
                        Utils.log(Utils.Level.ERROR, TAG, e.getMessage(), e);
                    }
                }

                /*
                 * Gingerbread does not parse policy flags via the Java InputManager
                 */
                if (params.args.length > 5) {
                    int policyFlags = (Integer) params.args[ params.args.length-1 ];

                    if ((keyEvent.getFlags() & KEYFLAG_INTERNAL) == KEYFLAG_INTERNAL && (policyFlags & POLICYFLAG_TRUSTED) != POLICYFLAG_TRUSTED) {
                        params.args[params.args.length - 1] = policyFlags|POLICYFLAG_TRUSTED;
                    }
                }
            }
        }
    };
}
