package com.spazedog.xposed.additionsgb.hooks;

import android.os.BatteryManager;
import android.util.Log;

import com.android.server.BatteryService;

import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.hooks.tools.XC_ClassHook;

import de.robv.android.xposed.XSharedPreferences;

public class PowerManagerServiceHook extends XC_ClassHook {
	
	protected BatteryReceiverHook mBatteryReceiver;

	public PowerManagerServiceHook(String className, ClassLoader classLoader) {
		super(!SDK_JB ? "com.android.server.PowerManagerService" : className, classLoader);
		
		if (!SDK_JB) {
			mBatteryReceiver = new BatteryReceiverHook("com.android.server.PowerManagerService$BatteryReceiver", classLoader);
		}
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
		
		if (Common.DEBUG) {
			Log.d(Common.PACKAGE_NAME, "Checking battery state");
		}
		
		if (powered != wasPowered && (pluggedAC || pluggedUSB)) {
			XSharedPreferences preferences = new XSharedPreferences(Common.PACKAGE_NAME, Common.HOOK_PREFERENCES);
			String type = powered ? "plug" : "unplug";
			String value = preferences.getString("display_usb_" + type, "default");
			
			if (Common.DEBUG) {
				Log.d(Common.PACKAGE_NAME, "Battery state has been updated");
			}
			
			if (!value.equals("default")) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Handling custom battery update configuration");
				}
				
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
	
	/*
	 * The below methods is used by Gingerbread and ICS which does not have shouldWakeUpWhenPluggedOrUnpluggedLocked()
	 */
	
	protected void forceUserActivityLocked() {
		invokeMethod("forceUserActivityLocked");
	}
	
	protected void updateWakeLockLocked() {
		invokeMethod("updateWakeLockLocked");
	}
	
	protected BatteryService getBatteryService() {
		return (BatteryService) getField("mBatteryService");
	}
	
	public class BatteryReceiverHook extends XC_ClassHook {
		protected Boolean mIsPowered;
		protected Integer mPlugType;
		
		protected final Object mLock = new Object();
		
		public BatteryReceiverHook(String className, ClassLoader classLoader) {
			super(className, classLoader);
		}
		
		public void xb_onReceive(final MethodHookParam param) {
			synchronized (mLock) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Checking battery state");
				}
				
				BatteryService batteryService = getBatteryService();
				
				/*
				 * If the device has just been booted, we only 
				 * cache the current state.
				 */
				if (mIsPowered != null && mPlugType != null) {
					Boolean wasPowered = mIsPowered;
					Integer oldPlugType = mPlugType;
					Integer plugType = batteryService.getPlugType();
					Boolean powered = batteryService.isPowered();
					Boolean pluggedAC = BatteryManager.BATTERY_PLUGGED_AC == plugType || BatteryManager.BATTERY_PLUGGED_AC == oldPlugType;
					Boolean pluggedUSB = BatteryManager.BATTERY_PLUGGED_USB == plugType || BatteryManager.BATTERY_PLUGGED_USB == oldPlugType;
					
					if (powered != wasPowered && (pluggedAC || pluggedUSB)) {
						if (Common.DEBUG) {
							Log.d(Common.PACKAGE_NAME, "Battery state has been updated");
						}
						
						XSharedPreferences preferences = new XSharedPreferences(Common.PACKAGE_NAME, Common.HOOK_PREFERENCES);
						String type = powered ? "plug" : "unplug";
						String value = preferences.getString("display_usb_" + type, "default");
						
						if (!value.equals("default")) {
							if (Common.DEBUG) {
								Log.d(Common.PACKAGE_NAME, "Handling custom battery update configuration");
							}
							
							updateWakeLockLocked();
							
							if (value.equals("on") 
									|| (pluggedAC && value.equals("ac")) 
										|| (pluggedUSB && value.equals("usb"))) {
								
								forceUserActivityLocked();
							}
							
							/*
							 * Disable default method
							 */
							param.setResult(false);
						}
					}
				}
				
				mIsPowered = batteryService.isPowered();
				mPlugType = batteryService.getPlugType();
			}
		}
	}
}
