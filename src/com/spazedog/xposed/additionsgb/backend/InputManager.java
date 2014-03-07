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

import android.os.Binder;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;

import com.spazedog.lib.reflecttools.ReflectTools;
import com.spazedog.lib.reflecttools.ReflectTools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectTools.ReflectMethod;
import com.spazedog.xposed.additionsgb.Common;

import de.robv.android.xposed.XC_MethodHook;

public class InputManager {
	public static final String TAG = InputManager.class.getName();
	
	protected int FLAG_INJECTED;
	
	protected Object mPtr;
	
	protected ReflectMethod mMethodNativeInjectInputEvent;
	
	protected static int FLAG_INTERNAL = 0x5000000;
	
	public static void init() {
		if(Common.DEBUG) Log.d(TAG, "Adding Input Manager Hook");

		if (android.os.Build.VERSION.SDK_INT >= 14) {
			InputManager hook = new InputManager();
			
			ReflectClass service = ReflectTools.getReflectClass(
					android.os.Build.VERSION.SDK_INT >= 16 ? 
							"com.android.server.input.InputManagerService" : 
								"com.android.server.wm.InputManager");
			
			service.inject(hook.hook_constructor);
			service.inject("injectInputEvent", hook.hook_injectInputEvent);
		}
	}
	
	protected XC_MethodHook hook_constructor = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			FLAG_INJECTED = (Integer) ReflectTools.getReflectClass("android.view.WindowManagerPolicy").getField("FLAG_INJECTED").get();
			
			if (android.os.Build.VERSION.SDK_INT >= 16) {
				/*
				 * Once API 20 is out, this will be Long instead of Integer. 
				 */
				mPtr = ReflectTools.getReflectClass(param.thisObject).getField("mPtr").get(param.thisObject);
				
				mMethodNativeInjectInputEvent = ReflectTools.getReflectClass(param.thisObject)
						.locateMethod("nativeInjectInputEvent", ReflectTools.MEMBER_MATCH_FAST, (mPtr instanceof Integer ? Integer.TYPE : Long.TYPE), InputEvent.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
				
			} else {
				mMethodNativeInjectInputEvent = ReflectTools.getReflectClass(param.thisObject)
						.locateMethod("nativeInjectInputEvent", ReflectTools.MEMBER_MATCH_FAST, InputEvent.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
			}
		}
	};

	protected XC_MethodHook hook_injectInputEvent = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			if (android.os.Build.VERSION.SDK_INT >= 16 && param.args[0] instanceof KeyEvent) {
				if ((((KeyEvent) param.args[0]).getFlags() & FLAG_INJECTED) == 0) {
					if(Common.debug()) Log.d(TAG, "Adding FLAG_INJECTED flag on KeyEvent " + ((KeyEvent) param.args[0]).getKeyCode());
					
					/*
					 * KitKat has an error where PolicyFlags[FLAG_INJECTED] will always show the key as injected in PhoneWindowManager#interceptKeyBeforeDispatching. 
					 * Since our PhoneWindowManager hook depends on being able to distinguish between button presses 
					 * and actual injected keys, we have added this small hook that will add the FLAG_INJECTED flag directly to the
					 * KeyEvent itself whenever it get's parsed though this service method.
					 */
					ReflectTools.getReflectClass(param.args[0]).getField("mFlags").set(param.args[0], ((KeyEvent) param.args[0]).getFlags() | FLAG_INJECTED);
					
				} else {
					if(Common.debug()) Log.d(TAG, "The KeyEvent " + ((KeyEvent) param.args[0]).getKeyCode() + " already contains the FLAG_INJECTED flag");
				}
			}
			
			if ((((KeyEvent) param.args[0]).getFlags() & FLAG_INTERNAL) != 0) {
				if (android.os.Build.VERSION.SDK_INT >= 16) {
					/*
					 * The original injectInputEvent method will disable repeating events. 
					 * So in order to trigger long press, we will have to inject the events ourself. 
					 */
					final int pid = Binder.getCallingPid();
					final int uid = Binder.getCallingUid();
					final long ident = Binder.clearCallingIdentity();
					
					try {
						mMethodNativeInjectInputEvent.invoke(param.thisObject, false, mPtr, param.args[0], pid, uid, param.args[1], 0, 0);
						
					} finally {
						Binder.restoreCallingIdentity(ident);
					}
					
					param.setResult(true);
					
				} else {
					/*
					 * ICS does not have an InputManagerService like JB+. Is has an InputManager
					 * that does the same combined with the WindowManagerService. 
					 * The job that JB+ does in one class, is split those two. 
					 */
					param.setResult(mMethodNativeInjectInputEvent.invoke(param.thisObject, false, param.args[0], param.args[1], param.args[2], param.args[3], param.args[4], 0));
				}
			}
		}
	};
}
