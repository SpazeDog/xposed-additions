package com.spazedog.xposed.additionsgb;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class ActivityMainSettings extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		PreferenceManager prefMgr = getPreferenceManager();
		prefMgr.setSharedPreferencesName(Common.HOOK_PREFERENCES);
		prefMgr.setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
		
		addPreferencesFromResource(R.xml.activity_main_settings);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		
		updateDescription("btn_longpress_delay");
		
		updateDescription("display_usb_plug");
		updateDescription("display_usb_unplug");
		
		/*
		 * TODO: Add support for 2.3+
		 */
		if (android.os.Build.VERSION.SDK_INT < 16) {
			getPreferenceManager().findPreference("display_usb_plug").setEnabled(false);
			getPreferenceManager().findPreference("display_usb_unplug").setEnabled(false);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		updateDescription(key);
	}
	
	protected void updateDescription(String name) {
		if (name.endsWith("_delay")) {
			Common.setDescriptionFromArray(this, getPreferenceScreen().findPreference(name), "delay");
			
		} else if (name.endsWith("plug")) {
			Common.setDescriptionFromArray(this, getPreferenceScreen().findPreference(name), "wakeplug");
		}
	}
}
