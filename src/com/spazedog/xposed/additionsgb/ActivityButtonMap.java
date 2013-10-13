package com.spazedog.xposed.additionsgb;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

public class ActivityButtonMap extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	
	private CheckBoxPreference mOptionEnable;
	private ListPreference mOptionLongPress;
	private ListPreference mOptionClick;
	
	private String mOptionArray = "";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setTitle(getIntent().getStringExtra("title"));
		
		PreferenceManager prefMgr = getPreferenceManager();
		prefMgr.setSharedPreferencesName(Common.HOOK_PREFERENCES);
		prefMgr.setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
		
		String mapType = getIntent().getStringExtra("mapType");
		String key = getIntent().getStringExtra("key");
		String button = "btn_" + mapType + "_" + key;
		Integer names = mapType.equals("on") ? R.array.remap_on_names : R.array.remap_off_names;
		Integer values = mapType.equals("on") ? R.array.remap_on_values : R.array.remap_off_values;
		
		mOptionArray = mapType.equals("on") ? "remap_on" : "remap_off";
		
		addPreferencesFromResource(R.xml.activity_button_map);
		
		PreferenceScreen preferenceScreen = getPreferenceScreen();
		
		mOptionEnable = new CheckBoxPreference(this);
		mOptionEnable.setTitle("Enable Re-Map");
		mOptionEnable.setKey(button + "_mapped");
		mOptionEnable.setPersistent(true);
		preferenceScreen.addPreference(mOptionEnable);
		
		PreferenceCategory preferenceCategory = new PreferenceCategory(this);
		preferenceCategory.setTitle("Button Map Options");
		preferenceScreen.addPreference(preferenceCategory);
		
		mOptionClick = new ListPreference(this);
		mOptionClick.setTitle("Click");
		mOptionClick.setKey(button + "_action_click");
		mOptionClick.setPersistent(true);
		mOptionClick.setEntryValues(values);
		mOptionClick.setEntries(names);
		mOptionClick.setDefaultValue("btn_" + key);
		preferenceScreen.addPreference(mOptionClick);
		
		mOptionLongPress = new ListPreference(this);
		mOptionLongPress.setTitle("Long Press");
		mOptionLongPress.setKey(button + "_action_press");
		mOptionLongPress.setPersistent(true);
		mOptionLongPress.setEntryValues(values);
		mOptionLongPress.setEntries(names);
		mOptionLongPress.setDefaultValue("disabled");
		preferenceScreen.addPreference(mOptionLongPress);
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
		
		updateDescription(mOptionEnable.getKey());
		updateDescription(mOptionClick.getKey());
		updateDescription(mOptionLongPress.getKey());
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		updateDescription(key);
	}
	
	protected void updateDescription(String name) {
		if (!name.endsWith("_mapped")) {
			Common.setDescriptionFromArray(this, getPreferenceScreen().findPreference(name), mOptionArray);
			
		} else {
			mOptionClick.setEnabled(mOptionEnable.isChecked());
			mOptionLongPress.setEnabled(mOptionEnable.isChecked());
		}
	}
}
