package com.spazedog.xposed.additionsgb.hooks;

import java.lang.reflect.Constructor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.spazedog.xposed.additionsgb.Common;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class PowerManager {
	
	public static final String TAG = Common.PACKAGE_NAME + "$PowerManager";

	private static Boolean OLD_SDK = false;
	
	private PowerManager() {}
	
	public static void inject() {
		Common.log(TAG, "Adding Power Management hooks");
		
		XC_MethodHook hook = new ShouldWakeUpWhenPluggedOrUnplugged();
		
		try {
			XposedBridge.hookAllMethods(
					XposedHelpers.findClass("com.android.server.power.PowerManagerService", null), 
					"shouldWakeUpWhenPluggedOrUnpluggedLocked", 
					hook
			);
			
		} catch(Throwable e) {
			OLD_SDK = true;
			
			/*
			 * We need to have access to this object from within the hook below
			 */
			XposedBridge.hookAllConstructors(
					XposedHelpers.findClass("com.android.server.PowerManagerService", null), 
					hook
			);
			
			XposedBridge.hookAllMethods(
					XposedHelpers.findClass("com.android.server.PowerManagerService$BatteryReceiver", null), 
					"onReceive", 
					hook
			);
		}
	}
	
	private static class ShouldWakeUpWhenPluggedOrUnplugged extends XC_MethodHook {
		
		private Boolean mIsPowered;
		private Integer mPlugType;
		
		private Object mParent;
		
		@Override
		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
			if (param.method instanceof Constructor) {
				Common.log(TAG, "Storing the PowerManagerService instance");
				
				/*
				 * In Gingerbread and ICS, the Plug/UnPlug wake is controlled from a 
				 * sub-class. We need to have access to the parent object from within the sub object.
				 */
				mParent = param.thisObject;
				
			} else {
				Object batteryService = OLD_SDK ? XposedHelpers.getObjectField(mParent, "mBatteryService") : null;
				Boolean wasPowered = OLD_SDK ? mIsPowered : (Boolean) param.args[0];
				Integer oldPlugType = OLD_SDK ? mPlugType : (Integer) param.args[1];
				Integer plugType = OLD_SDK ? (Integer) XposedHelpers.callMethod(batteryService, "getPlugType") : XposedHelpers.getIntField(param.thisObject, "mPlugType");
				Boolean powered = OLD_SDK ? (Boolean) XposedHelpers.callMethod(batteryService, "isPowered") : XposedHelpers.getBooleanField(param.thisObject, "mIsPowered");
				
				if (!OLD_SDK || (mIsPowered != null && mPlugType != null)) {
					Boolean pluggedAC = BatteryManager.BATTERY_PLUGGED_AC == plugType || BatteryManager.BATTERY_PLUGGED_AC == oldPlugType;
					Boolean pluggedUSB = BatteryManager.BATTERY_PLUGGED_USB == plugType || BatteryManager.BATTERY_PLUGGED_USB == oldPlugType;
					
					if (powered != wasPowered && (pluggedAC || pluggedUSB)) {
						if (Common.USBPlug.isStateEnabled(powered)) {
							Common.log(TAG, "Handling USB Plug/UnPlug display state");
							
							String action = Common.USBPlug.getStateAction(powered);
							
							if (OLD_SDK) {
								XposedHelpers.callMethod(mParent, "updateWakeLockLocked");
							}
							
							if (action.equals("on") 
									|| (pluggedAC && action.equals("ac")) 
										|| (pluggedUSB && action.equals("usb"))) {
								
								if (OLD_SDK) {
									XposedHelpers.callMethod(mParent, "forceUserActivityLocked");
									param.setResult(false);
									
								} else {
									param.setResult(true);
								}

							} else {
								param.setResult(false);
							}
						}
					}
				}
				
				if (OLD_SDK) {
					mIsPowered = powered;
					mPlugType = plugType;
				}
			}
		}
	}
}
