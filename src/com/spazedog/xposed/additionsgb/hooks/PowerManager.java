package com.spazedog.xposed.additionsgb.hooks;

import android.content.Context;
import android.os.BatteryManager;
import android.view.HapticFeedbackConstants;

import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.Common.HapticFeedbackLw;
import com.spazedog.xposed.additionsgb.tools.XposedTools;

import de.robv.android.xposed.XC_MethodHook;

public class PowerManager {
	
	public static final String TAG = Common.PACKAGE_NAME + "$PowerManager";

	private static Boolean OLD_SDK = false;
	
	private PowerManager() {}
	
	public static void inject() {
		Common.log(TAG, "Adding Power Management hooks");
		
		XC_MethodHook hook = new ShouldWakeUpWhenPluggedOrUnplugged();
		
		try {
			XposedTools.hookMethods("com.android.server.power.PowerManagerService", "shouldWakeUpWhenPluggedOrUnpluggedLocked", hook);
			
		} catch(Throwable e) {
			OLD_SDK = true;
			
			try {
				XposedTools.hookMethods("com.android.server.PowerManagerService$BatteryReceiver", "onReceive", hook);
				
			} catch(Throwable ei) { ei.printStackTrace(); }
		}
	}
	
	private static class ShouldWakeUpWhenPluggedOrUnplugged extends XC_MethodHook {
		
		private Boolean mIsPowered;
		private Integer mPlugType;
		HapticFeedbackLw mHapticFeedbackLw;
		
		@Override
		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
			Object parent = null;
			
			if (OLD_SDK) {
				parent = XposedTools.getParentObject(param.thisObject);
			}
			
			if (mHapticFeedbackLw == null) {
				mHapticFeedbackLw = new HapticFeedbackLw(
					(Context) XposedTools.getField(OLD_SDK ? parent : param.thisObject, "mContext")
				);
			}
			
			Object batteryService = OLD_SDK ? XposedTools.getField(parent, "mBatteryService") : null;
			Boolean wasPowered = OLD_SDK ? mIsPowered : (Boolean) param.args[0];
			Integer oldPlugType = OLD_SDK ? mPlugType : (Integer) param.args[1];
			Integer plugType = (Integer) (OLD_SDK ? XposedTools.callMethod(batteryService, "getPlugType") : XposedTools.getField(param.thisObject, "mPlugType"));
			Boolean powered = (Boolean) (OLD_SDK ? XposedTools.callMethod(batteryService, "isPowered") : XposedTools.getField(param.thisObject, "mIsPowered"));
			
			if (!OLD_SDK || (mIsPowered != null && mPlugType != null)) {
				Boolean pluggedAC = BatteryManager.BATTERY_PLUGGED_AC == plugType || BatteryManager.BATTERY_PLUGGED_AC == oldPlugType;
				Boolean pluggedUSB = BatteryManager.BATTERY_PLUGGED_USB == plugType || BatteryManager.BATTERY_PLUGGED_USB == oldPlugType;
				
				if (powered != wasPowered && (pluggedAC || pluggedUSB)) {
					if (Common.USBPlug.isStateEnabled(powered)) {
						Common.log(TAG, "Handling USB Plug/UnPlug display state");
						
						String action = Common.USBPlug.getStateAction(powered);
						
						if (OLD_SDK) {
							XposedTools.callMethod(parent, "updateWakeLockLocked");
						}
						
						if (action.equals("on") 
								|| (pluggedAC && action.equals("ac")) 
									|| (pluggedUSB && action.equals("usb"))) {
							
							if (OLD_SDK) {
								XposedTools.callMethod(parent, "forceUserActivityLocked");
								param.setResult(false);
								
							} else {
								param.setResult(true);
							}

						} else {
							Boolean isScreenOn = (Boolean) XposedTools.callMethod(OLD_SDK ? parent : param.thisObject, "isScreenOn");
							
							if (powered && !isScreenOn) {
								mHapticFeedbackLw.vibrate(new long[] {100, 200, 200, 200}, true);
							}
							
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
