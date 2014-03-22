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

import android.util.Log;
import android.view.KeyEvent;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.utils.ReflectException;
import com.spazedog.xposed.additionsgb.Common;

import de.robv.android.xposed.XC_MethodHook;

public class InputManager {
	public static final String TAG = InputManager.class.getName();
	
	protected static int FLAG_INJECTED;
	
	public static void init() {
		if(Common.DEBUG) Log.d(TAG, "Adding Input Manager Hook");

		/*
		 * This class does not exist in older Android Versions.
		 */
		if (android.os.Build.VERSION.SDK_INT >= 16) {
			try {
				InputManager hook = new InputManager();
				
				FLAG_INJECTED = (Integer) ReflectClass.forName("android.view.WindowManagerPolicy").findField("FLAG_INJECTED").getValue();
				
				ReflectClass.forName("com.android.server.input.InputManagerService").inject("injectInputEvent", hook.hook_injectInputEvent);
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}

	protected XC_MethodHook hook_injectInputEvent = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			if (param.args[0] instanceof KeyEvent) {
				if ((((KeyEvent) param.args[0]).getFlags() & FLAG_INJECTED) == 0) {
					if(Common.debug()) Log.d(TAG, "Adding FLAG_INJECTED flag on KeyEvent " + ((KeyEvent) param.args[0]).getKeyCode());
					
					/*
					 * KitKat has an error where PolicyFlags[FLAG_INJECTED] will always show the key as injected in PhoneWindowManager#interceptKeyBeforeDispatching. 
					 * Since our PhoneWindowManager hook depends on being able to distinguish between button presses 
					 * and actual injected keys, we have added this small hook that will add the FLAG_INJECTED flag directly to the
					 * KeyEvent itself whenever it get's parsed though this service method.
					 */
					try {
						new ReflectClass(param.args[0]).findField("mFlags").setValue(((KeyEvent) param.args[0]).getFlags() | FLAG_INJECTED);

					} catch (ReflectException e) {
						Log.e(TAG, e.getMessage(), e);
					}
					
				} else {
					if(Common.debug()) Log.d(TAG, "The KeyEvent " + ((KeyEvent) param.args[0]).getKeyCode() + " already contains the FLAG_INJECTED flag");
				}
			}
		}
	};
}
