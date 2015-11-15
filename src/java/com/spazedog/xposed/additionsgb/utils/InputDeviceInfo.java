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


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Pair;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.spazedog.lib.utilsLib.SparseMap;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class InputDeviceInfo {

    public static final int TYPE_EXTERNAL = 0x00000001;     // 1
    public static final int TYPE_KEYBOARD = 0x00000002;     // 2
    public static final int TYPE_NUMPAD = 0x00000004;       // 4
    public static final int TYPE_GAMEPAD = 0x00000008;      // 8
    public static final int TYPE_JOYSTICK = 0x00000010;     // 16
    public static final int TYPE_MOUSE = 0x00000020;        // 32
    public static final int TYPE_HEADSET = 0x00000040;      // 64

    private static final SparseMap<Integer> oTypeCache = new SparseMap<Integer>();
    private static final Map<Integer, Pair<String, String>> mKeyCodeNames = new SparseMap<Pair<String, String>>();
    private static final Map<Integer, Pair<String, String>> mKeyFlagNames = new SparseMap<Pair<String, String>>();
    private static Method oInputDeviceMethod;

    static {
        try {
                /*
                 * Simple and safe method that Google decided to hide for some reason.
                 * But it's no surprise as they have a tendency to limit developers options.
                 * It might be OpenSource, but it's anything but OpenAPI.
                 */
            if (oInputDeviceMethod == null) {
                oInputDeviceMethod = InputDevice.class.getDeclaredMethod("isExternal");
                oInputDeviceMethod.setAccessible(true);
            }

        } catch (NoSuchMethodException e) {}

        mKeyFlagNames.put(TYPE_EXTERNAL,                        new Pair<String, String>("TYPE_EXTERNAL",                   "External"));
        mKeyFlagNames.put(TYPE_KEYBOARD,                        new Pair<String, String>("TYPE_KEYBOARD",                   "Keyboard"));
        mKeyFlagNames.put(TYPE_NUMPAD,                          new Pair<String, String>("TYPE_NUMPAD",                     "Numpad"));
        mKeyFlagNames.put(TYPE_GAMEPAD,                         new Pair<String, String>("TYPE_GAMEPAD",                    "Gamepad"));
        mKeyFlagNames.put(TYPE_JOYSTICK,                        new Pair<String, String>("TYPE_JOYSTICK",                   "Joystick"));
        mKeyFlagNames.put(TYPE_MOUSE,                           new Pair<String, String>("TYPE_MOUSE",                      "Mouse"));
        mKeyFlagNames.put(TYPE_HEADSET,                         new Pair<String, String>("TYPE_HEADSET",                    "Headset"));

        mKeyCodeNames.put(KeyEvent.KEYCODE_VOLUME_UP,           new Pair<String, String>("KEYCODE_VOLUME_UP",               "Volume Up"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_VOLUME_DOWN,         new Pair<String, String>("KEYCODE_VOLUME_DOWN",             "Volume Down"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_SETTINGS,            new Pair<String, String>("KEYCODE_SETTINGS",                "Settings"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_SEARCH,              new Pair<String, String>("KEYCODE_SEARCH",                  "Search"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_POWER,               new Pair<String, String>("KEYCODE_POWER",                   "Power"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_NOTIFICATION,        new Pair<String, String>("KEYCODE_NOTIFICATION",            "Notification"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MUTE,                new Pair<String, String>("KEYCODE_MUTE",                    "Mic Mute"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MUSIC,               new Pair<String, String>("KEYCODE_MUSIC",                   "Music"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MOVE_HOME,           new Pair<String, String>("KEYCODE_MOVE_HOME",               "Home"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MENU,                new Pair<String, String>("KEYCODE_MENU",                    "Menu"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MEDIA_STOP,          new Pair<String, String>("KEYCODE_MEDIA_STOP",              "Media Stop"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MEDIA_REWIND,        new Pair<String, String>("KEYCODE_MEDIA_REWIND",            "Media Rewind"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MEDIA_RECORD,        new Pair<String, String>("KEYCODE_MEDIA_RECORD",            "Media Record"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MEDIA_PREVIOUS,      new Pair<String, String>("KEYCODE_MEDIA_PREVIOUS",          "Media Previous"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,    new Pair<String, String>("KEYCODE_MEDIA_PLAY_PAUSE",        "Media Play/Pause"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MEDIA_PLAY,          new Pair<String, String>("KEYCODE_MEDIA_PLAY",              "Media Play"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MEDIA_PAUSE,         new Pair<String, String>("KEYCODE_MEDIA_PAUSE",             "Media Pause"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MEDIA_NEXT,          new Pair<String, String>("KEYCODE_MEDIA_NEXT",              "Media Next"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,  new Pair<String, String>("KEYCODE_MEDIA_FAST_FORWARD",      "Media Fast Forward"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_HOME,                new Pair<String, String>("KEYCODE_HOME",                    "Home"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_FUNCTION,            new Pair<String, String>("KEYCODE_FUNCTION",                "Function"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_FOCUS,               new Pair<String, String>("KEYCODE_FOCUS",                   "Camera Focus"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_ENDCALL,             new Pair<String, String>("KEYCODE_ENDCALL",                 "End Call"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_DPAD_UP,             new Pair<String, String>("KEYCODE_DPAD_UP",                 "DPad Up"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_DPAD_RIGHT,          new Pair<String, String>("KEYCODE_DPAD_RIGHT",              "DPad Right"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_DPAD_LEFT,           new Pair<String, String>("KEYCODE_DPAD_LEFT",               "DPad Left"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_DPAD_DOWN,           new Pair<String, String>("KEYCODE_DPAD_DOWN",               "DPad Down"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_DPAD_CENTER,         new Pair<String, String>("KEYCODE_DPAD_CENTER",             "DPad Center"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_CAMERA,              new Pair<String, String>("KEYCODE_CAMERA",                  "Camera"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_CALL,                new Pair<String, String>("KEYCODE_CALL",                    "Call"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_BUTTON_START,        new Pair<String, String>("KEYCODE_BUTTON_START",            "Start"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_BUTTON_SELECT,       new Pair<String, String>("KEYCODE_BUTTON_SELECT",           "Select"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_BACK,                new Pair<String, String>("KEYCODE_BACK",                    "Back"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_APP_SWITCH,          new Pair<String, String>("KEYCODE_APP_SWITCH",              "App Switch"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_3D_MODE,             new Pair<String, String>("KEYCODE_3D_MODE",                 "3D Mode"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_ASSIST,              new Pair<String, String>("KEYCODE_ASSIST",                  "Assist"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_PAGE_UP,             new Pair<String, String>("KEYCODE_PAGE_UP",                 "Page Up"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_PAGE_DOWN,           new Pair<String, String>("KEYCODE_PAGE_DOWN",               "Page Down"));
        mKeyCodeNames.put(KeyEvent.KEYCODE_HEADSETHOOK,         new Pair<String, String>("KEYCODE_HEADSETHOOK",             "Headset Hook"));

        if (android.os.Build.VERSION.SDK_INT >= 11) {
            mKeyCodeNames.put(KeyEvent.KEYCODE_VOLUME_MUTE,     new Pair<String, String>("KEYCODE_VOLUME_MUTE",             "Volume Mute"));
            mKeyCodeNames.put(KeyEvent.KEYCODE_ZOOM_OUT,        new Pair<String, String>("KEYCODE_ZOOM_OUT",                "Zoom Out"));
            mKeyCodeNames.put(KeyEvent.KEYCODE_ZOOM_IN,         new Pair<String, String>("KEYCODE_ZOOM_IN",                 "Zoom In"));
        }
    }

    public static boolean isModifierKey(int keyCode) {
        if (!KeyEvent.isModifierKey(keyCode)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_CAPS_LOCK:
                case KeyEvent.KEYCODE_NUM_LOCK:
                case KeyEvent.KEYCODE_SCROLL_LOCK:

                    return true;
            }

            return false;
        }

        return true;
    }

    public static Set<Integer> getKeyCodeSet() {
        return mKeyCodeNames.keySet();
    }

    public static int maskMetaFlags(int metaFlags) {
        if (Build.VERSION.SDK_INT >= 11) {
            return metaFlags & (KeyEvent.META_ALT_ON | KeyEvent.META_CTRL_ON | KeyEvent.META_FUNCTION_ON | KeyEvent.META_SHIFT_ON | KeyEvent.META_SYM_ON);
        }

        /*
         * We don't have these flags to work with in GB, so no need for
         * special GB code here. Just return 0, the input will be 0 as well on GB devices.
         */
        return 0;
    }

    public static int getDeviceFlags(KeyEvent event) {
        return getDeviceFlags(event.getDevice());
    }

    public static int getDeviceFlags(InputDevice device) {
        int flags = oTypeCache.get(device.getId(), -1);

        if (flags == -1) {
            flags = 0;
            KeyCharacterMap map = device.getKeyCharacterMap();
            int source = device.getSources();
            int type = device.getKeyboardType();
            boolean hasHeadsetHook = false;

            if (Build.VERSION.SDK_INT >= 19) {
                hasHeadsetHook = device.hasKeys(KeyEvent.KEYCODE_HEADSETHOOK)[0];
            }

                /*
                 * There are devices with multiple functions, but we are not interested in whether or not a keyboard
                 * also has a numpad available, or joysticks with gamepad functions. We want to know what kind of device we have, and
                 * not what features it offers.
                 */
            if (type == InputDevice.KEYBOARD_TYPE_ALPHABETIC && (source & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD)
                flags |= TYPE_KEYBOARD;

            if (flags == 0 && (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK && (source & InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK)
                flags |= TYPE_JOYSTICK;

            if (flags == 0 && (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                flags |= TYPE_GAMEPAD;

            if (flags == 0 && (source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE || (source & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD)
                flags |= TYPE_MOUSE;

            if (flags == 0 && map.getKeyboardType() == KeyCharacterMap.NUMERIC)
                flags |= TYPE_NUMPAD;

            try {
                if (oInputDeviceMethod != null && (Boolean) oInputDeviceMethod.invoke(device)) {
                    flags |= TYPE_EXTERNAL;
                }

            } catch (Throwable e) {}

                /*
                 * Headset is always external and it cannot be a keyboard, mouse or anything else.
                 * So if flags only contains TYPE_EXTERNAL and we have a headset hook button, then it is 99% chance that we have a headset.
                 */
            if (flags == TYPE_EXTERNAL && hasHeadsetHook) {
                flags |= TYPE_HEADSET;
            }

            oTypeCache.put(device.getId(), flags);
        }

        return flags;
    }

    public static String flagsToString(int keyFlags, boolean presentable) {
        StringBuilder builder = new StringBuilder();
        boolean firstFlag = true;

        for (int flag : mKeyFlagNames.keySet()) {
            if ((keyFlags & flag) == flag) {
                if (!firstFlag) {
                    builder.append(",");

                } else {
                    firstFlag = false;
                }

                builder.append(mKeyFlagNames.get(flag).first);
            }
        }

        return builder.toString();
    }

    public static String flagsToLabel(int keyFlags) {
        return flagsToLabel(null, keyFlags);
    }

    public static String flagsToLabel(Context context, int keyFlags) {
        Resources res = context != null ? context.getResources() : Resources.getSystem();
        StringBuilder builder = new StringBuilder();
        boolean firstFlag = true;

        for (int flag : mKeyFlagNames.keySet()) {
            if ((keyFlags & flag) == flag) {
                if (!firstFlag) {
                    builder.append(",");

                } else {
                    firstFlag = false;
                }

                Pair<String, String> pair = mKeyFlagNames.get(flag);
                int identifier = res.getIdentifier(pair.first.toLowerCase(Locale.US), "string", Constants.PACKAGE_NAME);

                if (identifier > 0) {
                    builder.append(res.getString(identifier));

                } else {
                    builder.append(pair.second);
                }
            }
        }

        return builder.toString();
    }

    @SuppressLint("NewApi")
    public static String keyToString(int keyCode) {
        if (mKeyCodeNames.containsKey(keyCode)) {
            return mKeyCodeNames.get(keyCode).first;

        } else if (android.os.Build.VERSION.SDK_INT >= 12) {
            return KeyEvent.keyCodeToString(keyCode);
        }

        return "KEYCODE_" + keyCode;
    }

    public static String keyToLabel(int keyCode) {
        return keyToLabel(null, keyCode);
    }

    public static String keyToLabel(Context context, int keyCode) {
        String keyName = keyToString(keyCode);

        Resources res = context != null ? context.getResources() : Resources.getSystem();
        int identifier = res.getIdentifier(keyName.toLowerCase(Locale.US), "string", Constants.PACKAGE_NAME);

        if (identifier > 0) {
            return res.getString(identifier);

        } else if (mKeyCodeNames.containsKey(keyCode)) {
            return mKeyCodeNames.get(keyCode).second;
        }

        String[] codeWords = keyName.toLowerCase(Locale.US).split("_");
        StringBuilder builder = new StringBuilder();

        for (int i=1; i < codeWords.length; i++) {
            char[] codeChars = codeWords[i].trim().toCharArray();

            codeChars[0] = Character.toUpperCase(codeChars[0]);

            if (i > 1) {
                builder.append(" ");
            }

            builder.append(codeChars);
        }

        return builder.toString();
    }
}
