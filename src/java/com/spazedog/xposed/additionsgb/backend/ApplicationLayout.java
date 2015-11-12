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


import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Parcel;
import android.view.Window;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.lib.utilsLib.MultiParcelableBuilder;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;
import com.spazedog.xposed.additionsgb.utils.Utils.Type;

import java.util.List;

public class ApplicationLayout {
    public static final String TAG = ApplicationLayout.class.getName();

    public static class RotationConfig extends MultiParcelableBuilder {

        public final boolean OverwriteRotation;
        public final List<String> BlackList;

        public RotationConfig(boolean overwriteRotation, List<String> blackList) {
            OverwriteRotation = overwriteRotation;
            BlackList = blackList != null ? blackList : new SparseList<String>();
        }

        public RotationConfig(Parcel source) {
            OverwriteRotation = (Boolean) unparcelData(source, null);
            BlackList = (List<String>) unparcelData(source, getClass().getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);

            parcelData(OverwriteRotation, out, flags);
            parcelData(BlackList, out, flags);
        }
    }

    public static void init() {
        try {
            Utils.log(Level.INFO, TAG, "Instantiating ApplicationLayout");

            ApplicationLayout instance = new ApplicationLayout();

            ReflectClass.fromName("android.os.SystemProperties").bridge("getBoolean", instance.shouldEnableScreenRotation);
            ReflectClass.fromName("com.android.internal.policy.impl.PhoneWindow").bridge("generateLayout", instance.generateLayout);

        } catch (ReflectException e) {
            Utils.log(Level.ERROR, TAG, e.getMessage(), e);
        }
    }

    private RotationConfig mRotationConfig;

    private RotationConfig getRotationConfig() {
        if (mRotationConfig == null) {
            BackendServiceMgr backendMgr = BackendServiceMgr.getInstance();

            if (backendMgr != null && backendMgr.isServiceReady()) {
                mRotationConfig = null;
            }
        }

        return mRotationConfig;
    }

    public MethodBridge shouldEnableScreenRotation = new MethodBridge() {
        @Override
        public void bridgeBegin(BridgeParams params) {
            if (params.args.length > 0 && "lockscreen.rot_override".equals(params.args[0])) {
                RotationConfig rotationConfig = getRotationConfig();

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
            RotationConfig rotationConfig = getRotationConfig();

            if (context instanceof Activity && rotationConfig != null && rotationConfig.OverwriteRotation) {
                Activity activity = (Activity) context;
                String packageName = activity.getPackageName();

                if (activity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_USER && !mRotationConfig.BlackList.contains(packageName)) {
                    Utils.log(Type.LAYOUT, Level.DEBUG, TAG, "Overwriting rotation settings on package '" + packageName + "'");

                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                }
            }
        }
    };
}
