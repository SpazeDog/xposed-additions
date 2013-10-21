package com.spazedog.xposed.additionsgb;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

public class ActivityMainSettings extends PreferenceActivity implements OnPreferenceChangeListener {
	
	private CheckBoxPreference mPrefGlobalOrientation;
	
	private ListPreference mPrefUsbPlug;
	private ListPreference mPrefUsbUnPlug;
	
	private ListPreference mPrefDelayTap;
	private ListPreference mPrefDelayPress;
	
	private String[] mEntryNamesUsb;
	private String[] mEntryValuesUsb;
	
	private String[] mEntryNamesDelay;
	private String[] mEntryValuesDelayTap;
	private String[] mEntryValuesDelayPress;
	
	private Boolean mUpdated = false;
	
    @SuppressWarnings("deprecation")
	@Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	addPreferencesFromResource(R.xml.activity_main_settings);
    	
    	Common.loadSharedPreferences(this);
    	
    	mEntryNamesUsb = getResources().getStringArray(R.array.wakeplug_names);
    	mEntryValuesUsb = getResources().getStringArray(R.array.wakeplug_values);
    	
    	mEntryNamesDelay = getResources().getStringArray(R.array.delay_names);
    	mEntryValuesDelayTap = getResources().getStringArray(R.array.delay_tap_values);
    	mEntryValuesDelayPress = getResources().getStringArray(R.array.delay_press_values);
    	
    	PreferenceCategory layoutCategory = (PreferenceCategory) findPreference("category_layout");
    	PreferenceCategory usbPlugCategory = (PreferenceCategory) findPreference("category_usbplug");
    	PreferenceCategory buttonsCategory = (PreferenceCategory) findPreference("category_buttons");
    	
    	mPrefGlobalOrientation = new CheckBoxPreference(this);
    	mPrefGlobalOrientation.setKey( Common.AppLayout.KEY_APP_GLOBAL_ORIENTATION );
    	mPrefGlobalOrientation.setTitle(R.string.preference_title_layout_orientation);
    	mPrefGlobalOrientation.setSummary(R.string.preference_summary_layout_orientation);
    	mPrefGlobalOrientation.setChecked( Common.AppLayout.isGlobalOrientationEnabled() );
    	mPrefGlobalOrientation.setPersistent(false);
    	layoutCategory.addPreference(mPrefGlobalOrientation);
    	
    	mPrefUsbPlug = new ListPreference(this);
    	mPrefUsbPlug.setKey( Common.USBPlug.KEY_ACTION_PLUG );
    	mPrefUsbPlug.setTitle(R.string.preference_title_plug_wake);
    	mPrefUsbPlug.setOnPreferenceChangeListener(this);
    	mPrefUsbPlug.setEntries(mEntryNamesUsb);
    	mPrefUsbPlug.setEntryValues(mEntryValuesUsb);
    	mPrefUsbPlug.setValue( Common.USBPlug.getStateAction(true) );
    	mPrefUsbPlug.setPersistent(false);
    	usbPlugCategory.addPreference(mPrefUsbPlug);
    	
    	mPrefUsbUnPlug = new ListPreference(this);
    	mPrefUsbUnPlug.setKey( Common.USBPlug.KEY_ACTION_UNPLUG );
    	mPrefUsbUnPlug.setTitle(R.string.preference_title_unplug_wake);
    	mPrefUsbUnPlug.setOnPreferenceChangeListener(this);
    	mPrefUsbUnPlug.setEntries(mEntryNamesUsb);
    	mPrefUsbUnPlug.setEntryValues(mEntryValuesUsb);
    	mPrefUsbUnPlug.setValue( Common.USBPlug.getStateAction(false) );
    	mPrefUsbUnPlug.setPersistent(false);
    	usbPlugCategory.addPreference(mPrefUsbUnPlug);
    	
    	mPrefDelayTap = new ListPreference(this);
    	mPrefDelayTap.setKey( Common.Remap.KEY_DELAY_TAP );
    	mPrefDelayTap.setTitle(R.string.preference_title_delay_tap);
    	mPrefDelayTap.setOnPreferenceChangeListener(this);
    	mPrefDelayTap.setEntries(mEntryNamesDelay);
    	mPrefDelayTap.setEntryValues(mEntryValuesDelayTap);
    	mPrefDelayTap.setValue( "" + Common.Remap.getTapDelay() );
    	mPrefDelayTap.setPersistent(false);
    	buttonsCategory.addPreference(mPrefDelayTap);
    	
    	mPrefDelayPress = new ListPreference(this);
    	mPrefDelayPress.setKey( Common.Remap.KEY_DELAY_PRESS );
    	mPrefDelayPress.setTitle(R.string.preference_title_delay_press);
    	mPrefDelayPress.setOnPreferenceChangeListener(this);
    	mPrefDelayPress.setEntries(mEntryNamesDelay);
    	mPrefDelayPress.setEntryValues(mEntryValuesDelayPress);
    	mPrefDelayPress.setValue( "" + Common.Remap.getPressDelay() );
    	mPrefDelayPress.setPersistent(false);
    	buttonsCategory.addPreference(mPrefDelayPress);
    	
    	Intent intent = new Intent();
    	intent.setAction( Intent.ACTION_VIEW );
    	intent.setClass(this, ActivityRemapSettings.class);
    	
    	Preference remapKeys = new Preference(this);
    	remapKeys.setTitle(R.string.preference_title_remap);
    	remapKeys.setSummary(R.string.preference_summary_remap);
    	remapKeys.setIntent(intent);
    	buttonsCategory.addPreference(remapKeys);

    	Common.updateListSummary(mPrefUsbPlug, mEntryValuesUsb, mEntryNamesUsb);
    	Common.updateListSummary(mPrefUsbUnPlug, mEntryValuesUsb, mEntryNamesUsb);
    	
    	Common.updateListSummary(mPrefDelayTap, mEntryValuesDelayTap, mEntryNamesDelay);
    	Common.updateListSummary(mPrefDelayPress, mEntryValuesDelayPress, mEntryNamesDelay);
    }
    
	@Override
	protected void onPause() {
		super.onPause();

		if (mUpdated) {
			Common.requestConfigUpdate(this);
		}
	}
    
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
    	SharedPreferences preferences = Common.getSharedPreferences(this);
    	
    	if (preference == mPrefGlobalOrientation) {
    		preferences.edit().putBoolean( preference.getKey(), mPrefGlobalOrientation.isChecked()).apply();
    		
    		mUpdated = true;
    		
    		return true;
    	}
    	
    	return false;
    }

	@Override
	public boolean onPreferenceChange(Preference preference, Object value) {
		SharedPreferences preferences = Common.getSharedPreferences(this);
		
		((ListPreference) preference).setValue((String) value);
		
		preferences.edit().putString( preference.getKey(), (String) value).apply();
		
		mUpdated = true;
		
		if (preference == mPrefUsbPlug || 
				preference == mPrefUsbUnPlug) {
			
			Common.updateListSummary((ListPreference) preference, mEntryValuesUsb, mEntryNamesUsb);
			
		} else if (preference == mPrefDelayTap) {
			Common.updateListSummary((ListPreference) preference, mEntryValuesDelayTap, mEntryNamesDelay);
			
		} else if (preference == mPrefDelayPress) {
			Common.updateListSummary((ListPreference) preference, mEntryValuesDelayPress, mEntryNamesDelay);
		}
		
		return true;
	}
}
