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
	    	findAndHookMethod("com.android.internal.policy.impl.PhoneWindowManager", lpparam.classLoader, "interceptKeyBeforeQueueing", android.view.KeyEvent.class, Integer.TYPE, Boolean.TYPE, new XC_MethodHook() {
	    		@Override
	    		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
	    			if ( ((KeyEvent) param.args[0]).getKeyCode() == KeyEvent.KEYCODE_POWER && !((Boolean) param.args[2])) {
	    				if (Settings.System.getInt(((Context) XposedHelpers.getObjectField(param.thisObject, "mContext")).getContentResolver(), "disable_power_button", 0) == 1) {
	    					param.setResult(0);
	    				}
	    			}
	    		}
    		});
    		
    		findAndHookMethod("com.android.internal.policy.impl.keyguard.KeyguardViewManager", lpparam.classLoader, "shouldEnableScreenRotation", new XC_MethodReplacement() {
				@Override
				protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
					return Settings.System.getInt(((Context) XposedHelpers.getObjectField(param.thisObject, "mContext")).getContentResolver(), "enable_lockscreen_rotation", 0) == 1;
				}
    		});

    		findAndHookMethod("com.android.server.power.PowerManagerService", lpparam.classLoader, "shouldWakeUpWhenPluggedOrUnpluggedLocked", Boolean.TYPE, Integer.TYPE, Boolean.TYPE, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					if (Settings.System.getInt(((Context) XposedHelpers.getObjectField(param.thisObject, "mContext")).getContentResolver(), "wake_on_usb_change", 0) != 1) {
						param.setResult(false);
						
					} else {
						// Make sure that shouldWakeUpWhenPluggedOrUnpluggedLocked() does not stop the device from waking
						XposedHelpers.setBooleanField(param.thisObject, "mWakeUpWhenPluggedOrUnpluggedConfig", true);
					}
				}
    		});
    	}
    }
}
