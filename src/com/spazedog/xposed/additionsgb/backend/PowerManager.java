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

import com.spazedog.lib.reflecttools.ReflectTools;
import com.spazedog.lib.reflecttools.ReflectTools.ReflectException;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.Common.Index;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;

import de.robv.android.xposed.XC_MethodHook;

public final class PowerManager {
	public static final String TAG = PowerManager.class.getName();
	
	protected static Boolean OLD_SDK = false;
	
	protected final Object mLock = new Object();
	
	protected Context mContext;
	
	protected Object mBatteryService;
	
	protected XServiceManager mPreferences;

	protected Integer mPlugType;
	
	public static void init() {
		if(Common.DEBUG) Log.d(TAG, "Adding Power Manager Hook");
		
		PowerManager hooks = new PowerManager();
		
		try {
			ReflectTools.getReflectClass("com.android.server.power.PowerManagerService").inject("shouldWakeUpWhenPluggedOrUnpluggedLocked", hooks.hook_shouldWakeUpWhenPluggedOrUnpluggedLocked);
			ReflectTools.getReflectClass("com.android.server.power.PowerManagerService").inject("init", hooks.hook_init);
			
		} catch (ReflectException e) {
			ReflectTools.getReflectClass("com.android.server.PowerManagerService$BatteryReceiver").inject("onReceive", hooks.hook_shouldWakeUpWhenPluggedOrUnpluggedLocked);
			ReflectTools.getReflectClass("com.android.server.PowerManagerService").inject("init", hooks.hook_init);
			
			OLD_SDK = true;
		}
	}
	
	protected XC_MethodHook hook_init = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			if(Common.DEBUG) Log.d(TAG, "Initiating Power Manager Hook");
			mContext = (Context) ReflectTools.getReflectClass(param.thisObject).getField("mContext").get(param.thisObject);
			mBatteryService = ReflectTools.getReflectClass(param.thisObject).getField("mBatteryService").get(param.thisObject);
			
			mPreferences = XServiceManager.getInstance();
			mPreferences.registerContext(mContext);
		}
	};
	
	protected XC_MethodHook hook_shouldWakeUpWhenPluggedOrUnpluggedLocked = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			synchronized (mLock) {
				if(Common.DEBUG) Log.d(TAG, "Received USB Plug/UnPlug state change");
				
				Boolean wasPowered = (Boolean) (OLD_SDK ? ReflectTools.getReflectClass(param.thisObject).locateField("mIsPowered").get(param.thisObject) : param.args[0]);
				Integer oldPlugType = OLD_SDK ? mPlugType : (Integer) param.args[1];
				Integer plugType = (Integer) (OLD_SDK ? ReflectTools.getReflectClass(mBatteryService).locateMethod("getPlugType").invoke(mBatteryService) : ReflectTools.getReflectClass(param.thisObject).getField("mPlugType").get(param.thisObject));
				Boolean powered = (Boolean) (OLD_SDK ? ReflectTools.getReflectClass(mBatteryService).locateMethod("isPowered").invoke(mBatteryService) : ReflectTools.getReflectClass(param.thisObject).getField("mIsPowered").get(param.thisObject));
				
				if (!OLD_SDK || mPlugType != null) {
					Boolean pluggedAC = BatteryManager.BATTERY_PLUGGED_AC == plugType || BatteryManager.BATTERY_PLUGGED_AC == oldPlugType;
					Boolean pluggedUSB = BatteryManager.BATTERY_PLUGGED_USB == plugType || BatteryManager.BATTERY_PLUGGED_USB == oldPlugType;
					
					if (powered != wasPowered && (pluggedAC || pluggedUSB)) {
						Boolean moduleStatus = mPreferences.getBoolean(
								powered ? Index.bool.key.usbPlugSwitch : Index.bool.key.usbUnPlugSwitch, 
										powered ? Index.bool.value.usbPlugSwitch : Index.bool.value.usbUnPlugSwitch);
						
						if (moduleStatus) {
							String configAction = mPreferences.getString(
									powered ? Index.string.key.usbPlugAction : Index.string.key.usbUnPlugAction, 
											powered ? Index.string.value.usbPlugAction : Index.string.value.usbUnPlugAction);
							
							if(Common.DEBUG) Log.d(TAG, "Handling USB Plug/UnPlug display state");
							
							if (OLD_SDK) {
								ReflectTools.getReflectClass(param.thisObject).locateField("mIsPowered").set(param.thisObject, powered);
							}
							
							if (configAction.equals("on") 
									|| (pluggedAC && configAction.equals("ac")) 
										|| (pluggedUSB && configAction.equals("usb"))) {
								
								if(Common.DEBUG) Log.d(TAG, "Turning display on");
								
								if (OLD_SDK) {
									ReflectTools.getReflectClass(param.thisObject).locateMethod("forceUserActivityLocked").invoke(param.thisObject);
									param.setResult(false);
									
								} else {
									param.setResult(true);
								}
								
							} else {
								if(Common.DEBUG) Log.d(TAG, "Disabling default handler");
								
								param.setResult(false);
							}
						}
						
					} else if (powered == wasPowered) {
						param.setResult(false);
					}
				}
				
				if (OLD_SDK) {
					mPlugType = plugType;
				}
			}
		}
	};
}
