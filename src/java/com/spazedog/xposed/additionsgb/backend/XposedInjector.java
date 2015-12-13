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

import android.content.Context;

import com.spazedog.lib.reflecttools.bridge.InitBridge;
import com.spazedog.xposed.additionsgb.backend.pwm.PhoneWindowManager;
import com.spazedog.xposed.additionsgb.backend.service.XService;

public final class XposedInjector  extends InitBridge {
    public static final String TAG = XposedInjector.class.getName();

	public static final void initialize() throws Throwable {
		InitBridge.initialize(XposedInjector.class);
	}

    @Override
	public void onSystemInit(Context systemContext) {
        XService.init(systemContext);

        LogcatMonitor.init();
        ApplicationLayout.init();
        PowerManager.init();
        PhoneWindowManager.init();
        InputManager.init();
	}
}
