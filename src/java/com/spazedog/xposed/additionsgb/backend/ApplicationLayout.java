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


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Parcel;
import android.view.Window;
import android.view.WindowManager.LayoutParams;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.lib.utilsLib.MultiParcelableBuilder;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.backend.ssl.SystemStateProxy.SystemState;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;
import com.spazedog.xposed.additionsgb.utils.Utils.Type;

import java.util.List;

public class ApplicationLayout {
    public static final String TAG = ApplicationLayout.class.getName();

    @SuppressLint("ParcelCreator")
    public static class LayoutConfig extends MultiParcelableBuilder {

        public final boolean OverwriteRotation;
        public final List<String> BlackList;
        public final List<String> LaunchSelection;

        public LayoutConfig(boolean overwriteRotation, List<String> blackList, List<String> launchSelection) {
            OverwriteRotation = overwriteRotation;
            BlackList = blackList != null ? blackList : new SparseList<String>();
            LaunchSelection = launchSelection != null ? launchSelection : new SparseList<String>();
        }

        public LayoutConfig(Parcel source) {
            OverwriteRotation = (Boolean) unparcelData(source, null);
            BlackList = (List<String>) unparcelData(source, Utils.getAppClassLoader());
            LaunchSelection = (List<String>) unparcelData(source, Utils.getAppClassLoader());
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);

            parcelData(OverwriteRotation, out, flags);
            parcelData(BlackList, out, flags);
            parcelData(LaunchSelection, out, flags);
        }
    }

    public static void init() {
        try {
            Utils.log(Level.INFO, TAG, "Instantiating ApplicationLayout");

            ApplicationLayout instance = new ApplicationLayout();

            ReflectClass.fromName("android.os.SystemProperties").bridge("getBoolean", instance.shouldEnableScreenRotation);

            try {
                ReflectClass.fromName("com.android.internal.policy.impl.PhoneWindow").bridge("generateLayout", instance.generateLayout);    // SDK < 23

            } catch (ReflectException e) {
                ReflectClass.fromName("com.android.internal.policy.PhoneWindow").bridge("generateLayout", instance.generateLayout);         // SDK >= 23
            }

        } catch (ReflectException e) {
            Utils.log(Level.ERROR, TAG, e.getMessage(), e);
        }
    }

    private LayoutConfig getLayoutConfig() {
        LayoutConfig settings = null;
        BackendServiceMgr backendMgr = BackendServiceMgr.getInstance();

        if (backendMgr != null && backendMgr.isServiceReady()) {
            settings = backendMgr.getLayoutConfig();
        }

        return settings;
    }

    public MethodBridge shouldEnableScreenRotation = new MethodBridge() {
        @Override
        public void bridgeBegin(BridgeParams params) {
            if (params.args.length > 0 && "lockscreen.rot_override".equals(params.args[0])) {
                LayoutConfig rotationConfig = getLayoutConfig();

                if (rotationConfig != null && rotationConfig.OverwriteRotation) {
                    Utils.log(Type.LAYOUT, Level.DEBUG, TAG, "Overwriting rotation settings on LockScreen");

                    params.setResult(true);
                }
            }
        }
    };

    public MethodBridge generateLayout = new MethodBridge() {
        @Override
        public void bridgeEnd(BridgeParams params) {
            Window window = (Window) params.receiver;
            Context context = window.getContext();
            LayoutConfig layoutConfig = getLayoutConfig();

            if (context instanceof Activity && layoutConfig != null) {
                Activity activity = (Activity) context;
                String packageName = activity.getPackageName();

                /*
                 * Force Rotation
                 */
                if (layoutConfig.OverwriteRotation && activity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_USER && !layoutConfig.BlackList.contains(packageName)) {
                    Utils.log(Type.LAYOUT, Level.DEBUG, TAG, "Overwriting rotation settings on package '" + packageName + "'");

                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                }

                /*
                 * Allow application to turn on screen and launch on top of the lockscreen
                 */
                if (layoutConfig.LaunchSelection.contains(packageName)) {
                    BackendServiceMgr backendMgr = BackendServiceMgr.getInstance();
                    SystemState systemState = backendMgr.getSystemState();

                    if (systemState != null) {
                        int flags = 0;

                        if (!systemState.isScreenOn()) {
                            flags |= (LayoutParams.FLAG_KEEP_SCREEN_ON | LayoutParams.FLAG_TURN_SCREEN_ON);
                        }

                        if (systemState.isScreenLocked()) {
                            flags |= (LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_DISMISS_KEYGUARD);
                        }

                        if (flags > 0) {
                            Utils.log(Type.LAYOUT, Level.DEBUG, TAG, "Allowing package '" + packageName + "' to launch on top of lockscreen");

                            window.addFlags(flags);
                        }
                    }
                }
            }
        }
    };
}
