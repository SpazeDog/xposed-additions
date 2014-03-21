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
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.Common.Index;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;

import de.robv.android.xposed.XC_MethodHook;

public final class ApplicationLayout {
	public static final String TAG = ApplicationLayout.class.getName();
	
	public static void init() {
		if(Common.DEBUG) Log.d(TAG, "Adding Application Layout Hook");
		
		XC_MethodHook hook = new LayoutHook();
		
		ReflectClass.forName("com.android.internal.policy.impl.PhoneWindow").inject("generateLayout", hook);
		ReflectClass.forName("android.app.Activity").inject("setRequestedOrientation", hook);
		
		if (android.os.Build.VERSION.SDK_INT > 15) {
			try {
				/*
				 * TODO: Find a way for pre-jellybean.
				 * TODO: Also make a better way for Jellybean+ as this does not always work as it should
				 */
				ReflectClass.forName("com.android.internal.policy.impl.keyguard").inject("shouldEnableScreenRotation", hook);
				
			} catch (Throwable e) {}
		}
	}
	
	private static class LayoutHook extends XC_MethodHook {
		
		public Boolean mGetSettings = true;
		public Boolean mEnableRotation = false;
		public Boolean mBlackListed = false;
		
		public List<String> mBlackList = new ArrayList<String>();
		
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			/*
			 * This instance is going to be parsed across multiple processes.
			 * Each process will not get any changes made by other processes, so
			 * for each new process we need a new setup. 
			 */
			if (mGetSettings) {
				XServiceManager preferences = XServiceManager.getInstance();
				
				mGetSettings = false;
				mEnableRotation = preferences.getBoolean(Index.bool.key.layoutRotationSwitch, Index.bool.value.layoutRotationSwitch);
				
				if (mEnableRotation && preferences.isPackageUnlocked()) {
					mBlackList = preferences.getStringArray(Index.array.key.layoutRotationBlacklist, Index.array.value.layoutRotationBlacklist);
				}
			}
			
			mBlackListed = mBlackList.contains( AndroidAppHelper.currentPackageName() );
			
			if (mEnableRotation && !mBlackListed) {
				if(Common.debug()) Log.d(TAG, "+ " + param.method.getName() + ": Allowing Rotation for '" + AndroidAppHelper.currentPackageName() + "'");
				
				if ("setRequestedOrientation".equals(param.method.getName())) {
					param.args[0] = ActivityInfo.SCREEN_ORIENTATION_USER;
					
				} else if ("shouldEnableScreenRotation".equals(param.method.getName())) {
					param.setResult(true);
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
