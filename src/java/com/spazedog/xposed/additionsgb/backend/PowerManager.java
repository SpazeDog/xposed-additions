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
import android.util.Log;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.ReflectMember.Result;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Settings;

public final class PowerManager {
	public static final String TAG = PowerManager.class.getName();

    /*
     * These are from android.os.BatteryManager
     */
    private static final int BATTERY_PLUGGED_AC = 1;
    private static final int BATTERY_PLUGGED_USB = 2;
	
	public static void init() {
		Log.i(TAG, "Adding Power Manager Hook");

		PowerManager instance = new PowerManager();

		try {
			if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
				ReflectClass pms = ReflectClass.fromName("com.android.server.power.PowerManagerService");
				pms.bridge("shouldWakeUpWhenPluggedOrUnpluggedLocked", instance.hook_shouldWakeUpWhenPluggedOrUnpluggedLocked);

				if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
					pms.bridge("systemReady", instance.hook_init);

				} else {
					pms.bridge("init", instance.hook_init);
				}

			} else {
				ReflectClass.fromName("com.android.server.PowerManagerService").bridge("init", instance.hook_init);
				ReflectClass.fromName("com.android.server.PowerManagerService$BatteryReceiver").bridge("onReceive", instance.hook_shouldWakeUpWhenPluggedOrUnpluggedLocked);
			}

		} catch (ReflectException e) {
            Log.e(TAG, e.getMessage(), e);
		}
	}

    private boolean mIsReady = false;

    private ReflectClass mPowerManager;
    private ReflectClass mBatteryService;
    private XServiceManager mBackendMgr;

    private boolean mIsPowered = false;
    private boolean mWasPowered = false;
    private int mPlugType = 0;
	
	protected MethodBridge hook_init = new MethodBridge() {
		@Override
        public void bridgeEnd(BridgeParams params) {
            try {
                Log.i(TAG, "Configuring PowerManager");

                mBackendMgr = XServiceManager.getInstance();

                if (mBackendMgr != null) {
                    mPowerManager = ReflectClass.fromReceiver(params.receiver);

                    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                        mBatteryService = (ReflectClass) mPowerManager.findField("mBatteryManagerInternal").getValue(Result.INSTANCE);

                    } else {
                        mBatteryService = (ReflectClass) mPowerManager.findField("mBatteryService").getValue(Result.INSTANCE);
                    }

                    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                        mIsPowered = (Boolean) mBatteryService.invokeMethod("isPowered", BATTERY_PLUGGED_AC|BATTERY_PLUGGED_USB);

                    } else {
                        mIsPowered = (Boolean) mBatteryService.invokeMethod("isPowered");
                    }

                    mPlugType = (Integer) mBatteryService.invokeMethod("getPlugType");
                    mIsReady = true;

                } else {
                    Log.e(TAG, "The backend service is not loaded");
                }

            } catch (ReflectException e) {
                Log.e(TAG, e.getMessage(), e);
            }
		}
	};
	
	protected MethodBridge hook_shouldWakeUpWhenPluggedOrUnpluggedLocked = new MethodBridge() {
        @Override
        public void bridgeBegin(BridgeParams params) {
            if (mIsReady) {
                try {
                    mWasPowered = mIsPowered;

                    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                        mIsPowered = (Boolean) mBatteryService.invokeMethod("isPowered", BATTERY_PLUGGED_AC|BATTERY_PLUGGED_USB);

                    } else {
                        mIsPowered = (Boolean) mBatteryService.invokeMethod("isPowered");
                    }

                    if (mIsPowered) {
                        /*
                         * If not powered, then we do not have any plug type.
                         * We still need the old value though as to know which type we just unplugged from.
                         * So only overwrite if we have a new type to use.
                         */
                        mPlugType = (Integer) mBatteryService.invokeMethod("getPlugType");
                    }

                    if(Common.debug()) Log.d(TAG, "Received USB " + (mIsPowered ? "Plug" : "Unplug") + " state");

                    boolean moduleStatus = mBackendMgr.getBoolean(mIsPowered ? Settings.USB_CONNECTION_SWITCH_PLUG : Settings.USB_CONNECTION_SWITCH_UNPLUG);

                    if (moduleStatus) {
                        if (mIsPowered != mWasPowered) {
                            String configAction = mBackendMgr.getString(mIsPowered ? Settings.USB_CONNECTION_PLUG : Settings.USB_CONNECTION_UNPLUG);

                            if (configAction.equals("on")
                                    || (mPlugType == BATTERY_PLUGGED_AC && configAction.equals("ac"))
                                    || (mPlugType == BATTERY_PLUGGED_USB && configAction.equals("usb"))) {

                                if(Common.debug()) Log.d(TAG, "Allowing screen to turn on");

                                if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                                    params.setResult(true);
                                    return;

                                } else {
                                    mPowerManager.invokeMethod("forceUserActivityLocked");
                                }
                            }
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                            params.setResult(false);

                        } else {
                            params.setResult(null);
                        }

                    } else {
                        if(Common.debug()) Log.d(TAG, "Using System Default settings");
                    }

                } catch (ReflectException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
	};
}
