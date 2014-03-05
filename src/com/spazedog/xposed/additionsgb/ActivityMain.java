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

package com.spazedog.xposed.additionsgb;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;

public class ActivityMain extends PreferenceActivity {
	
	private XServiceManager mPreferences;
	
	private Boolean mSetup = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.activity_main);
	}
	
    @Override
    protected void onStart() {
    	super.onStart();
    	
    	mPreferences = XServiceManager.getInstance();
    }
	
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	if (mPreferences == null) {
    		new AlertDialog.Builder(this)
    		.setTitle(R.string.alert_title_module_disabled)
    		.setMessage(R.string.alert_text_module_disabled)
    		.setCancelable(false)
    		.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
                public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
					ActivityMain.this.finish();
				}
    		})
    		.show();
    		
    	} else {
    		setup();
    	}
    }
    
	@Override
	protected void onPause() {
		super.onPause();
	}
	
    @Override
    protected void onStop() {
    	super.onStop();
    	
    	mPreferences = null;
    }
    
    private void setup() {
    	if (mSetup != (mSetup = true)) {
    		if (mPreferences.isPackageUnlocked()) {
    			getPreferenceScreen().removePreference( findPreference("pro_link") );
    		}
    		
    		findPreference("usbplug_link").setIntent(new Intent(Intent.ACTION_VIEW).setClass(this, ActivityScreenUSB.class));
    		findPreference("layout_link").setIntent(new Intent(Intent.ACTION_VIEW).setClass(this, ActivityScreenLayout.class));
    		findPreference("buttons_link").setIntent(new Intent(Intent.ACTION_VIEW).setClass(this, ActivityScreenRemapMain.class));
    	}
    }
}
