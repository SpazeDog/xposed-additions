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

import android.content.Context;
import android.os.BatteryManager;
import android.util.Log;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.utils.ReflectConstants.Match;
import com.spazedog.lib.reflecttools.utils.ReflectException;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Settings;

import de.robv.android.xposed.XC_MethodHook;

public final class PowerManager {
	public static final String TAG = PowerManager.class.getName();
	
	protected static Boolean OLD_SDK = false;
	
	protected final Object mLock = new Object();
	
	protected Context mContext;
	
	protected ReflectClass mPowerManager;
	protected ReflectClass mBatteryService;
	
	protected XServiceManager mPreferences;

	protected Integer mPlugType;
	protected Boolean mIsPowered;
	protected Boolean mInitiated = false;
	
	public static void init() {
		if(Common.DEBUG) Log.d(TAG, "Adding Power Manager Hook");
		
		PowerManager hooks = new PowerManager();
		
		try {
			ReflectClass pms = ReflectClass.forName("com.android.server.power.PowerManagerService");
			
			pms.inject("init", hooks.hook_init);
			pms.inject("shouldWakeUpWhenPluggedOrUnpluggedLocked", hooks.hook_shouldWakeUpWhenPluggedOrUnpluggedLocked);
			
		} catch (ReflectException ignore) {
			try {
				OLD_SDK = true;
				
				ReflectClass.forName("com.android.server.PowerManagerService").inject("init", hooks.hook_init);
				ReflectClass.forName("com.android.server.PowerManagerService$BatteryReceiver").inject("onReceive", hooks.hook_shouldWakeUpWhenPluggedOrUnpluggedLocked);
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}
	
	protected XC_MethodHook hook_init = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			try {
				if(Common.debug()) Log.d(TAG, "Initiating Power Manager Hook");
				
				mPowerManager = ReflectClass.forReceiver(param.thisObject);
				mBatteryService = mPowerManager.findField("mBatteryService").getValueToInstance();
				
				mContext = (Context) mPowerManager.findField("mContext").getValue();
				
				mPreferences = XServiceManager.getInstance();
				
				if (mPreferences == null) {
					throw new ReflectException("XService has not been started", null);
				}
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
				
				ReflectClass.forReceiver(param.thisObject).removeInjections();
			}
		}
	};
	
	protected XC_MethodHook hook_shouldWakeUpWhenPluggedOrUnpluggedLocked = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			synchronized (mLock) {
				try {
					if(Common.debug()) Log.d(TAG, "Received USB Plug/UnPlug state change");
	
					Boolean powered = OLD_SDK ? (Boolean) mBatteryService.findMethod("isPowered").invoke() : (Boolean) mBatteryService.findMethod("isPowered", Match.BEST, Integer.TYPE).invoke(BatteryManager.BATTERY_PLUGGED_AC | BatteryManager.BATTERY_PLUGGED_USB);
					Integer plugType = (Integer) mBatteryService.findMethod("getPlugType").invoke();
					Integer oldPlugType = mPlugType;
					Boolean wasPowered = mIsPowered;
					
					if (mInitiated) {
						Boolean pluggedAC = BatteryManager.BATTERY_PLUGGED_AC == plugType || BatteryManager.BATTERY_PLUGGED_AC == oldPlugType;
						Boolean pluggedUSB = BatteryManager.BATTERY_PLUGGED_USB == plugType || BatteryManager.BATTERY_PLUGGED_USB == oldPlugType;
						
						if (powered != wasPowered && (pluggedAC || pluggedUSB)) {
							Boolean moduleStatus = mPreferences.getBoolean(
									powered ? Settings.USB_CONNECTION_SWITCH_PLUG : Settings.USB_CONNECTION_SWITCH_UNPLUG);
							
							if (moduleStatus) {
								String configAction = mPreferences.getString(
										powered ? Settings.USB_CONNECTION_PLUG : Settings.USB_CONNECTION_UNPLUG);
								
								if(Common.debug()) Log.d(TAG, "Handling USB Plug/UnPlug display state");
								
								if (OLD_SDK) {
									mPowerManager.findFieldDeep("mIsPowered").setValue(powered);
								}
								
								if (configAction.equals("on") 
										|| (pluggedAC && configAction.equals("ac")) 
											|| (pluggedUSB && configAction.equals("usb"))) {
									
									if(Common.debug()) Log.d(TAG, "Turning display on");
									
									if (OLD_SDK) {
										mPowerManager.findMethodDeep("forceUserActivityLocked").invoke();
										param.setResult(false);
										
									} else {
										param.setResult(true);
									}
									
								} else {
									if(Common.debug()) Log.d(TAG, "Disabling default handler");
									
									param.setResult(false);
								}
							}
							
						} else if (powered == wasPowered) {
							param.setResult(false);
						}
						
					} else {
						mInitiated = true;
					}
	
					mIsPowered = powered;
					mPlugType = plugType;
					
				} catch (ReflectException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
		}
	};
}
