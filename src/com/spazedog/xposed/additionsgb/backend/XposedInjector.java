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

import com.spazedog.xposed.additionsgb.backend.service.XService;

import de.robv.android.xposed.IXposedHookZygoteInit;

public final class XposedInjector implements IXposedHookZygoteInit {
	@Override
	public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
		/*
		 * Register our custom system service
		 */
		XService.init();
		
		/*
		 * Register Hooks
		 */
		PowerManager.init();
		ApplicationLayout.init();
		PhoneWindowManager.init();
		InputManager.init();
		ViewConfiguration.init();
	}
}
