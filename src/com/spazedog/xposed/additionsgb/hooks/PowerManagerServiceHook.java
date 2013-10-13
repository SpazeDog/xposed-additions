package com.spazedog.xposed.additionsgb.hooks;

import android.os.BatteryManager;

import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.hooks.tools.XC_ClassHook;

import de.robv.android.xposed.XSharedPreferences;

public class PowerManagerServiceHook extends XC_ClassHook {

	public PowerManagerServiceHook(String className, ClassLoader classLoader) {
		super(className, classLoader);
	}
	
	/**
	 * JellyBean uses arguments shouldWakeUpWhenPluggedOrUnpluggedLocked(Boolean wasPowered, Integer oldPlugType, Boolean dockedOnWirelessCharger)
	 */
	public void xb_shouldWakeUpWhenPluggedOrUnpluggedLocked(final MethodHookParam param) {
		Boolean wasPowered = (Boolean) param.args[0];
		Integer oldPlugType = (Integer) param.args[1];
		Integer plugType = (Integer) getField("mPlugType");
		Boolean powered = (Boolean) getField("mIsPowered");
		Boolean pluggedAC = BatteryManager.BATTERY_PLUGGED_AC == plugType || BatteryManager.BATTERY_PLUGGED_AC == oldPlugType;
		Boolean pluggedUSB = BatteryManager.BATTERY_PLUGGED_USB == plugType || BatteryManager.BATTERY_PLUGGED_USB == oldPlugType;
		
		if (powered != wasPowered && (pluggedAC || pluggedUSB)) {
			XSharedPreferences preferences = new XSharedPreferences(Common.PACKAGE_NAME, Common.HOOK_PREFERENCES);
			String type = powered ? "plug" : "unplug";
			String value = preferences.getString("display_usb_" + type, "default");
			
			if (!value.equals("default")) {
				if (value.equals("on") 
						|| (pluggedAC && value.equals("ac")) 
							|| (pluggedUSB && value.equals("usb"))) {
					
					param.setResult(true);
					
				} else {
					param.setResult(false);
				}
			}
		}
	}
}
