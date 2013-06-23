package com.spazedog.xposed.additions;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import android.content.Context;
import android.provider.Settings;
import android.view.KeyEvent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class BehaviorHooks implements IXposedHookLoadPackage {
	
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
    	if (lpparam.packageName.equals("android")) {
	    	findAndHookMethod("com.android.internal.policy.impl.PhoneWindowManager", lpparam.classLoader, "interceptKeyBeforeQueueing", android.view.KeyEvent.class, Integer.TYPE, Boolean.TYPE, PhoneWindowManager_interceptKeyBeforeQueueing);
    		findAndHookMethod("com.android.internal.policy.impl.keyguard.KeyguardViewManager", lpparam.classLoader, "shouldEnableScreenRotation", KeyguardViewManager_shouldEnableScreenRotation);
    		findAndHookMethod("com.android.server.power.PowerManagerService", lpparam.classLoader, "shouldWakeUpWhenPluggedOrUnpluggedLocked", Boolean.TYPE, Integer.TYPE, Boolean.TYPE, PowerManagerService_shouldWakeUpWhenPluggedOrUnpluggedLocked);
    	}
    }
    
    public final XC_MethodHook PhoneWindowManager_interceptKeyBeforeQueueing = new XC_MethodHook() {
		@Override
		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
			if ( (((KeyEvent) param.args[0]).getKeyCode() == KeyEvent.KEYCODE_POWER || ((KeyEvent) param.args[0]).getKeyCode() == KeyEvent.KEYCODE_HOME) && !((Boolean) param.args[2])) {
				String button = ((KeyEvent) param.args[0]).getKeyCode() == KeyEvent.KEYCODE_POWER ? "power" : "home";
				String allowed = Settings.System.getString(((Context) XposedHelpers.getObjectField(param.thisObject, "mContext")).getContentResolver(), "xposed_wakeon_button");
				
				if (allowed != null && !allowed.contains(button)) {
					param.setResult(0);
				}
			}
		}
    };
    
    public final XC_MethodHook PowerManagerService_shouldWakeUpWhenPluggedOrUnpluggedLocked = new XC_MethodHook() {
		@Override
		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
			String actions = Settings.System.getString(((Context) XposedHelpers.getObjectField(param.thisObject, "mContext")).getContentResolver(), "xposed_wakeon_usb_change");
			
			if (actions == null || (actions.contains("pluggedin") && !((Boolean) param.args[0])) || (actions.contains("pluggedout") && ((Boolean) param.args[0]))) {
				// Make sure that shouldWakeUpWhenPluggedOrUnpluggedLocked() does not stop the device from waking
				XposedHelpers.setBooleanField(param.thisObject, "mWakeUpWhenPluggedOrUnpluggedConfig", true);
				
			} else {
				param.setResult(false);
			}
		}
    };
    
    public final XC_MethodReplacement KeyguardViewManager_shouldEnableScreenRotation = new XC_MethodReplacement() {
		@Override
		protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
			return Settings.System.getInt(((Context) XposedHelpers.getObjectField(param.thisObject, "mContext")).getContentResolver(), "xposed_enable_lockscreen_rotation", 0) == 1;
		}
    };
}
