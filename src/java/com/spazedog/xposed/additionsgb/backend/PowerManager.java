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


import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Parcel;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.ReflectMember.Result;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.MultiParcelableBuilder;
import com.spazedog.xposed.additionsgb.backend.service.BackendService;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr.ServiceListener;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

public class PowerManager implements ServiceListener {
    public static final String TAG = PowerManager.class.getName();

    /*
     * These are from android.os.BatteryManager
     */
    private static final int BATTERY_PLUGGED_AC = 1;
    private static final int BATTERY_PLUGGED_USB = 2;
    private static final int BATTERY_PLUGGED_WIRELESS = 4;
    private static final int BATTERY_PLUGGED_ANY;

    static {
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
            BATTERY_PLUGGED_ANY = BATTERY_PLUGGED_AC | BATTERY_PLUGGED_USB | BATTERY_PLUGGED_WIRELESS;

        } else {
            BATTERY_PLUGGED_ANY = BATTERY_PLUGGED_AC | BATTERY_PLUGGED_USB;
        }
    }

    public static class PowerPlugConfig extends MultiParcelableBuilder {

        public final static int PLUGGED_AC = 0x00000001;
        public final static int PLUGGED_USB = 0x00000002;
        public final static int PLUGGED_WIRELESS = 0x00000004;
        public final static int PLUGGED_DEFAULT = 0xFFFFFFFF;

        public final int Plug;
        public final int UnPlug;

        public PowerPlugConfig(int plug, int unPlug) {
            Plug = plug;
            UnPlug = unPlug;
        }

        public PowerPlugConfig(Parcel source) {
            Plug = (Integer) unparcelData(source, null);
            UnPlug = (Integer) unparcelData(source, null);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);

            parcelData(Plug, out, flags);
            parcelData(UnPlug, out, flags);
        }
    }

    private boolean mIsReady = false;

    private ReflectClass mPowerManager;
    private ReflectClass mBatteryService;
    private BackendServiceMgr mBackendMgr;
    private PowerPlugConfig mConfig;

    private boolean mIsPowered = false;
    private boolean mWasPowered = false;
    private int mCurPlugType = 0;
    private int mOldPlugType = 0;

    @Override
    public void onReceiveMsg(int type, HashBundle data) {
        switch (type) {
            case Constants.BRC_MGR_UPDATE:
                if ((data.getInt("flags") & BackendService.FLAG_RELOAD_CONFIG) != 0) {
                    mConfig = (PowerPlugConfig) data.getParcelable("powerConfig");

                    if (!mIsReady) {
                        mIsReady = true;
                    }
                }
        }
    }

    public static void init() {
        Utils.log(Level.INFO, TAG, "Instantiating PowerManager");

        PowerManager instance = new PowerManager();

        try {
            if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                ReflectClass pms = ReflectClass.fromName("com.android.server.power.PowerManagerService");
                pms.bridge("shouldWakeUpWhenPluggedOrUnpluggedLocked", instance.shouldWakeUpWhenPluggedOrUnplugged);

                if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                    pms.bridge("systemReady", instance.systemReady);

                } else {
                    pms.bridge("init", instance.systemReady);
                }

            } else {
                ReflectClass.fromName("com.android.server.PowerManagerService").bridge("init", instance.systemReady);
                ReflectClass.fromName("com.android.server.PowerManagerService$BatteryReceiver").bridge("onReceive", instance.shouldWakeUpWhenPluggedOrUnplugged);
            }

        } catch (ReflectException e) {
            Utils.log(Level.ERROR, TAG, e.getMessage(), e);
        }
    }

    public MethodBridge systemReady = new MethodBridge() {
        @Override
        public void bridgeEnd(BridgeParams params) {
            try {
                Utils.log(Level.INFO, TAG, "Configuring PowerManager");

                mBackendMgr = BackendServiceMgr.getInstance();

                if (mBackendMgr != null) {
                    mConfig = mBackendMgr.getPowerConfig();
                    mPowerManager = ReflectClass.fromReceiver(params.receiver);

                    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                        mBatteryService = (ReflectClass) mPowerManager.findField("mBatteryManagerInternal").getValue(Result.INSTANCE);

                    } else {
                        mBatteryService = (ReflectClass) mPowerManager.findField("mBatteryService").getValue(Result.INSTANCE);
                    }

                    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                        mIsPowered = (Boolean) mBatteryService.invokeMethod("isPowered", BATTERY_PLUGGED_ANY);

                    } else {
                        mIsPowered = (Boolean) mBatteryService.invokeMethod("isPowered");
                    }

                    mCurPlugType = (Integer) mBatteryService.invokeMethod("getPlugType");
                    mIsReady = mConfig != null;

                    mBackendMgr.attachListener(PowerManager.this);

                } else {
                    Utils.log(Level.ERROR, TAG, "The backend service is not loaded");
                }

            } catch (ReflectException e) {
                Utils.log(Level.ERROR, TAG, e.getMessage(), e);
            }
        }
    };

    public MethodBridge shouldWakeUpWhenPluggedOrUnplugged = new MethodBridge() {
        @Override
        public void bridgeBegin(BridgeParams params) {
            if (mIsReady) {
                try {
                    Utils.log(Level.DEBUG, TAG, "Received USB Plug/UnPlug state change");

                    mWasPowered = mIsPowered;
                    mOldPlugType = mCurPlugType;
                    mCurPlugType = (Integer) mBatteryService.invokeMethod("getPlugType");

                    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                        mIsPowered = (Boolean) mBatteryService.invokeMethod("isPowered", BATTERY_PLUGGED_ANY);

                    } else {
                        mIsPowered = (Boolean) mBatteryService.invokeMethod("isPowered");
                    }

                    int plugConfig = mIsPowered ? mConfig.Plug : mConfig.UnPlug;
                    int plugType = mIsPowered ? mCurPlugType : mOldPlugType;

                    if (plugConfig != PowerPlugConfig.PLUGGED_DEFAULT) {
                        if (mIsPowered != mWasPowered || mCurPlugType != mOldPlugType) {
                            switch (plugType) {
                                case BATTERY_PLUGGED_AC: if ((plugConfig & PowerPlugConfig.PLUGGED_AC) == 0) { break; }
                                case BATTERY_PLUGGED_USB: if ((plugConfig & PowerPlugConfig.PLUGGED_USB) == 0) { break; }
                                case BATTERY_PLUGGED_WIRELESS: if ((plugConfig & PowerPlugConfig.PLUGGED_WIRELESS) == 0) { break; }

                                    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                                        params.setResult(true);

                                    } else {
                                        mPowerManager.invokeMethod("forceUserActivityLocked");
                                    }
                            }
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1 && params.getResult() == null) {
                            params.setResult(false);

                        } else {
                            params.setResult(null);
                        }
                    }

                } catch (ReflectException e) {
                    Utils.log(Level.ERROR, TAG, e.getMessage(), e);
                }
            }
        }
    };
}
