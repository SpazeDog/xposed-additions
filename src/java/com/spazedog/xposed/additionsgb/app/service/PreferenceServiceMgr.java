/*
 * This file is part of the Xposed Additions Project: https://github.com/spazedog/xposed-additions
 *
 * Copyright (c) 2015 Daniel Bergløv
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

package com.spazedog.xposed.additionsgb.app.service;


import android.content.Context;

import com.spazedog.xposed.additionsgb.app.service.PreferenceService.Proxy;

public class PreferenceServiceMgr {

    public static final String TAG = PreferenceServiceMgr.class.getName();

    protected Proxy mProxy;

    public PreferenceServiceMgr(Context context) {
        mProxy = (Proxy) PreferenceService.bind(context);
    }

    public void close() {
        PreferenceService.unbind(mProxy);

        mProxy = null;
    }

    public void putConfig(String name, Object value) {
        mProxy.putConfig(name, value);
    }

    public int getIntConfig(String name) {
        return getIntConfig(name, 0);
    }

    public int getIntConfig(String name, int defValue) {
        return mProxy.getIntConfig(name, defValue);
    }

    public String getStringConfig(String name) {
        return getStringConfig(name, null);
    }

    public String getStringConfig(String name, String defValue) {
        return mProxy.getStringConfig(name, defValue);
    }
}
