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

package com.spazedog.xposed.additionsgb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.spazedog.xposed.additionsgb.backend.service.XService;
import com.spazedog.xposed.additionsgb.utils.SettingsHelper.SettingsData;

public class SettingsReceiver extends BroadcastReceiver {
	public static final String TAG = XService.class.getName();

	@Override
	public void onReceive(Context context, Intent intent) {
		if(Common.DEBUG) Log.d(TAG, "Writing preferences to file");
		
		SettingsData data = intent.getParcelableExtra("data");
		
		if (data != null) {
			SharedPreferences preferences = context.getSharedPreferences(Common.PREFERENCE_FILE, Context.MODE_WORLD_READABLE);
			SharedPreferences.Editor editor = preferences.edit();
			
			editor.clear();
			
			for (String key : data.keySet()) {
				editor.putString(key, data.getString(key));
			}
			
			editor.commit();
		}
	}
}
