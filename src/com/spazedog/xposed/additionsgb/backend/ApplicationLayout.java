/*
 * This file is part of the Xposed Additions Project: https://github.com/spazedog/xposed-additions
 *  
 * Copyright (c) 2014 Daniel Bergløv
 *
 * Xposed Additions is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Xposed Additions is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Xposed Additions. If not, see <http://www.gnu.org/licenses/>
 */

package com.spazedog.xposed.additionsgb.backend;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.util.Log;
import android.view.Window;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.utils.ReflectException;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.Common.Index;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;

import de.robv.android.xposed.XC_MethodHook;

public final class ApplicationLayout {
	public static final String TAG = ApplicationLayout.class.getName();
	
	protected Boolean mConfigureKeyguard = true;
	protected Boolean mKeyguardOverwriteRotation = false;
	
	protected Boolean mGetSettings = true;
	protected Boolean mEnableRotation = false;
	protected Boolean mBlackListed = false;
	
	protected List<String> mBlackList = new ArrayList<String>();
	
	public static void init() {
		if(Common.DEBUG) Log.d(TAG, "Adding Application Layout Hook");
		
		ApplicationLayout appLayout = new ApplicationLayout();

		try {
			ReflectClass.forName("com.android.internal.policy.impl.PhoneWindow").inject("generateLayout", appLayout.hook_orientationAndLayout);
			ReflectClass.forName("android.app.Activity").inject("setRequestedOrientation", appLayout.hook_orientationAndLayout);
			
			/*
			 * The keyguard has it's own way of handling this. This is actually handled in KeyguardViewManager, but this class
			 * get's moved around in almost every release, and each version introduces new ways of handling orientation settings. 
			 * The System Property is the only persistent control. 
			 */
			ReflectClass.forName("android.os.SystemProperties").inject("getBoolean", appLayout.hook_keyguardLayout);
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	protected XC_MethodHook hook_keyguardLayout = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			if (param.args.length > 0 && "lockscreen.rot_override".equals(param.args[0])) {
				if (mConfigureKeyguard) {
					XServiceManager preferences = XServiceManager.getInstance();
					
					if (preferences != null) {
						mConfigureKeyguard = false;
						mKeyguardOverwriteRotation = preferences.getBoolean(Index.bool.key.layoutRotationSwitch, Index.bool.value.layoutRotationSwitch);
					}
				}
				
				if (mKeyguardOverwriteRotation) {
					param.setResult(true);
				}
			}
		}
	};
	
	protected XC_MethodHook hook_orientationAndLayout = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			/*
			 * This instance is going to be parsed across multiple processes.
			 * Each process will not get any changes made by other processes, so
			 * for each new process we need a new setup. 
			 */
			if (mGetSettings) {
				XServiceManager preferences = XServiceManager.getInstance();
				
				if (preferences != null) {
					mGetSettings = false;
					mEnableRotation = preferences.getBoolean(Index.bool.key.layoutRotationSwitch, Index.bool.value.layoutRotationSwitch);
					
					if (mEnableRotation && preferences.isPackageUnlocked()) {
						mBlackList = preferences.getStringArray(Index.array.key.layoutRotationBlacklist, Index.array.value.layoutRotationBlacklist);
					}
					
				} else {
					return;
				}
			}
			
			mBlackListed = mBlackList.contains( AndroidAppHelper.currentPackageName() );
			
			if (mEnableRotation && !mBlackListed) {
				if(Common.debug()) Log.d(TAG, "+ " + param.method.getName() + ": Allowing Rotation for '" + AndroidAppHelper.currentPackageName() + "'");
				
				if ("setRequestedOrientation".equals(param.method.getName())) {
					param.args[0] = ActivityInfo.SCREEN_ORIENTATION_USER;
				}
				
			} else if (mEnableRotation) {
				if(Common.debug()) Log.d(TAG, "- " + param.method.getName() + ": Rotation has been blacklisted for '" + AndroidAppHelper.currentPackageName() + "'");
			}
		}
		
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			if (mEnableRotation && !mBlackListed) {
				if(Common.debug()) Log.d(TAG, "+ " + param.method.getName() + ": Allowing Rotation for '" + AndroidAppHelper.currentPackageName() + "'");
				
				if ("generateLayout".equals(param.method.getName())) {
					Window window = (Window) param.thisObject;
					Context context = window.getContext();
					
					if (context instanceof Activity) {
						((Activity) context).setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_USER );
					}
				}
				
			} else if (mEnableRotation) {
				if(Common.debug()) Log.d(TAG, "- " + param.method.getName() + ": Rotation has been blacklisted for '" + AndroidAppHelper.currentPackageName() + "'");
			}
		}
	};
}
