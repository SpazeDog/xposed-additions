package com.spazedog.xposed.additionsgb.hooks;

import com.spazedog.xposed.additionsgb.Common;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public final class XposedHook implements IXposedHookZygoteInit, IXposedHookLoadPackage {
	
	@Override
	public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
		Common.loadSharedPreferences(null);

		PhoneWindowManager.inject();
		PowerManager.inject();
		ApplicationLayout.inject();
	}
	
	@Override
	public void handleLoadPackage(LoadPackageParam params) throws Throwable {
		if (params.packageName.equals("android")) {
			// Not being used at the moment
		}
	}
}
