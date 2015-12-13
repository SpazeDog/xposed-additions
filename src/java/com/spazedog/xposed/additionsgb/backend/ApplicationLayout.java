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
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import android.view.Window;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Settings;

import java.util.ArrayList;
import java.util.List;

public final class ApplicationLayout {
	public static final String TAG = ApplicationLayout.class.getName();

    public static void init() {
        try {
            Log.i(TAG, "Instantiating ApplicationLayout");

            ApplicationLayout instance = new ApplicationLayout();

            ReflectClass.fromName("android.os.SystemProperties").bridge("getBoolean", instance.shouldEnableScreenRotation);

            if (VERSION.SDK_INT > VERSION_CODES.LOLLIPOP_MR1) {
                /*
                 * While PhoneWindowManager moved, so did PhoneWindow
                 */
                ReflectClass.fromName("com.android.internal.policy.PhoneWindow").bridge("generateLayout", instance.generateLayout);

            } else {
                ReflectClass.fromName("com.android.internal.policy.impl.PhoneWindow").bridge("generateLayout", instance.generateLayout);
            }

        } catch (ReflectException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private Boolean mForceRotation;
    private List<String> mBlacklist;

    private boolean hasForcedRotation() {
        if (mForceRotation == null) {
            XServiceManager backendMgr = XServiceManager.getInstance();

            if (backendMgr != null && backendMgr.isServiceReady()) {
                mForceRotation = backendMgr.getBoolean(Settings.LAYOUT_ENABLE_GLOBAL_ROTATION);
            }
        }

        return mForceRotation != null && mForceRotation;
    }

    private List<String> getRotationBlacklist() {
        if (mBlacklist == null) {
            XServiceManager backendMgr = XServiceManager.getInstance();

            if (backendMgr != null && backendMgr.isServiceReady()) {
                if (backendMgr.getBoolean(Settings.LAYOUT_ENABLE_GLOBAL_ROTATION) && backendMgr.isPackageUnlocked()) {
                    mBlacklist = backendMgr.getStringArray(Settings.LAYOUT_GLOBAL_ROTATION_BLACKLIST);

                    if (mBlacklist == null) {
                        mBlacklist = new ArrayList<String>();
                    }
                }
            }
        }

        return mBlacklist;
    }

    public MethodBridge shouldEnableScreenRotation = new MethodBridge() {
        @Override
        public void bridgeBegin(BridgeParams params) {
            if (params.args.length > 0 && "lockscreen.rot_override".equals(params.args[0])) {
                boolean rotate = hasForcedRotation();

                if (rotate) {
                    if(Common.debug()) Log.d(TAG, "Overwriting rotation settings on LockScreen");

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
            boolean rotate = hasForcedRotation();

            if (context instanceof Activity && rotate) {
                Activity activity = (Activity) context;
                String packageName = activity.getPackageName();
                List<String> blacklist = getRotationBlacklist();

                if (activity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_USER && blacklist == null || !blacklist.contains(packageName)) {
                    if(Common.debug()) Log.d(TAG, "Overwriting rotation settings on package '" + packageName + "'");

                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                }
            }
        }
    };
}
