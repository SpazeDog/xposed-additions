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

import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.spazedog.xposed.additionsgb.backend.service.XService;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.SettingsHelper.SettingsData;

public class SettingsReceiver extends BroadcastReceiver {
	public static final String TAG = SettingsReceiver.class.getName();

	@SuppressWarnings("unchecked")
	@Override
	public void onReceive(Context context, Intent intent) {
		if(Common.DEBUG) Log.d(TAG, "Starting settings broadcast receiver");
		
		String action = intent.getAction();
		SharedPreferences preferences = context.getSharedPreferences(Common.PREFERENCE_FILE, Context.MODE_PRIVATE);
		
		if (actionSaveSettings(action)) {
			SettingsData data = null;
			XServiceManager manager = XServiceManager.getInstance();
			
			if (manager.isServiceReady()) {
				data = manager.getSettingsData();
				
				if (data != null) {
					data.pack();
				}
			}
			
			if (data != null && data.changed()) {
				if(Common.DEBUG) Log.d(TAG, "Saving preferences from the service");
				
				Toast.makeText(context, "Saving configurations", Toast.LENGTH_LONG).show();
				
				SharedPreferences.Editor editor = preferences.edit();
				
				editor.clear();
				
				for (String key : data.keySet()) {
					String value = data.getString(key);
					
					if (value != null) {
						editor.putString(key, value);
					}
				}
				
				editor.commit();
			}
			
		} else if (actionRestoreSettings(action)) {
			if(Common.DEBUG) Log.d(TAG, "Restoring preferences to the service");
			
			Map<String, Object> data = null;
			
			try {
				data = (Map<String, Object>) preferences.getAll();
				
			} catch (NullPointerException e) {}
			
			if (data != null) {
				XServiceManager manager = XServiceManager.getInstance();
				
				if (manager.isServiceReady()) {
					Toast.makeText(context, "Restoring configurations", Toast.LENGTH_LONG).show();
					
					manager.setSettingsData( new SettingsData(data).unpack() );
				}
			}
		}
	}
	
	private boolean actionRestoreSettings(String action) {
		return action != null && action.equals( Constants.Intent.ACTION_XSERVICE_READY );
	}
	
	private boolean actionSaveSettings(String action) {
		return action != null && action.equals( Constants.Intent.ACTION_XSERVICE_SHUTDOWN );
	}
}
