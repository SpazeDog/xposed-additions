package com.spazedog.xposed.additionsgb.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.view.Window;

import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.tools.XposedTools;

import de.robv.android.xposed.XC_MethodHook;

public class ApplicationLayout {
	
	public static final String TAG = Common.PACKAGE_NAME + "$ApplicationLayout";
	
	private ApplicationLayout() {}
	
	public static void inject() {
		Common.log(TAG, "Adding Application Layout hooks");
		
		XposedTools.hookMethods(
				"com.android.internal.policy.impl.PhoneWindow", 
				"generateLayout", 
				new GenerateLayout()
		);
		
		XposedTools.hookMethods(
				"android.app.Activity", 
				"setRequestedOrientation", 
				new SetRequestedOrientation()
		);
		
		try {
			/*
			 * TODO: Find a way for pre-jellybean
			 */
			if (android.os.Build.VERSION.SDK_INT > 15) {
				XposedTools.hookMethods(
						"com.android.internal.policy.impl.keyguard", 
						"shouldEnableScreenRotation", 
						new ShouldEnableScreenRotation()
				);
			}
			
		} catch (Throwable e) {}
	}
	
	private static class GenerateLayout extends XC_MethodHook {
		@Override
		protected void afterHookedMethod(MethodHookParam param) throws Throwable {
			if ( Common.AppLayout.isGlobalOrientationEnabled() ) {
				Common.log(TAG, "Changing orientation settings on new layout [" + param.thisObject.getClass().getName() + "]");
				
				Window window = (Window) param.thisObject;
				Context context = window.getContext();
				
				if (context instanceof Activity) {
					((Activity) context).setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_USER );
				}
			}
		}
	}
	
	private static class SetRequestedOrientation extends XC_MethodHook {
		@Override
		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
			if ( Common.AppLayout.isGlobalOrientationEnabled() ) {
				Common.log(TAG, "Changing arguments on setRequestedOrientation() [" + param.thisObject.getClass().getName() + "]");
				
				param.args[0] = ActivityInfo.SCREEN_ORIENTATION_USER;
			}
		}
	}
	
	private static class ShouldEnableScreenRotation extends XC_MethodHook {
		@Override
		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
			if ( Common.AppLayout.isGlobalOrientationEnabled() ) {
				param.setResult(true);
			}
		}
	}
}
