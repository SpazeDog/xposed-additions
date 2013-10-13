package com.spazedog.xposed.additionsgb.hooks;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public final class HookInjector implements IXposedHookLoadPackage {
	
	protected PhoneWindowManagerHook mPhoneWindowManager;
	protected PowerManagerServiceHook mPowerManagerService;
	
	@Override
	public void handleLoadPackage(LoadPackageParam params) throws Throwable {
		if (params.packageName.equals("android")) {
			mPhoneWindowManager = new PhoneWindowManagerHook("com.android.internal.policy.impl.PhoneWindowManager", params.classLoader);
			mPowerManagerService = new PowerManagerServiceHook("com.android.server.power.PowerManagerService", params.classLoader);
		}
	}
}
