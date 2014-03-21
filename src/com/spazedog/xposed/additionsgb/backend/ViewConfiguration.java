package com.spazedog.xposed.additionsgb.backend;

import android.util.Log;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.xposed.additionsgb.Common;

import de.robv.android.xposed.XC_MethodHook;

public class ViewConfiguration {
	public static final String TAG = PhoneWindowManager.class.getName();
	
	public static void init() {
		if(Common.DEBUG) Log.d(TAG, "Adding View Configuration Hook");
		
		ViewConfiguration hooks = new ViewConfiguration();
		ReflectClass wc = ReflectClass.forName("android.view.ViewConfiguration");

		wc.inject("getLongPressTimeout", hooks.hook_getLongPressTimeout);
		wc.inject("getGlobalActionKeyTimeout", hooks.hook_getGlobalActionKeyTimeout);
	}
	
	protected XC_MethodHook hook_getLongPressTimeout = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			param.setResult(360);
		}
	};
	
	protected XC_MethodHook hook_getGlobalActionKeyTimeout = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			param.setResult(100);
		}
	};
}
