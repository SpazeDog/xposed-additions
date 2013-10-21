package com.spazedog.xposed.additionsgb;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

public class ActivityRemapButton extends PreferenceActivity implements OnPreferenceClickListener, OnPreferenceChangeListener {
	
	private Integer mKeyCode;
	
	private Preference mPrefRemoveButton;
	private ListPreference mPrefOptions;
	
	private CheckBoxPreference mPrefOnEnaled;
	private ListPreference mPrefOnClick;
	private ListPreference mPrefOnTap;
	private ListPreference mPrefOnPress;
	
	private CheckBoxPreference mPrefOffEnaled;
	private ListPreference mPrefOffClick;
	private ListPreference mPrefOffTap;
	private ListPreference mPrefOffPress;
	
	private PreferenceCategory mCategSleep;
	private PreferenceCategory mCategAwake;
	
	private String[] mOptionsListNames;
	private String[] mOptionsListValues;
	
	private String[] mRemapOnNames;
	private String[] mRemapOnValues;
	
	private String[] mRemapOffNames;
	private String[] mRemapOffValues;
	
	private Boolean mUpdated = false;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	mKeyCode = getIntent().getIntExtra("keycode", 0);

    	setTitle(Common.keycodeToString(mKeyCode));
    	
    	addPreferencesFromResource(R.xml.activity_remap_button);
    	
    	Common.loadSharedPreferences(this);
    	
    	String[] onNames = getResources().getStringArray(R.array.remap_actions_on_names);
    	String[] onValues = getResources().getStringArray(R.array.remap_actions_on_values);
    	String[] keyArray = Common.getSharedPreferences(this).getString(Common.Remap.KEY_COLLECTION, "").split(",");
    	List<String> newNames = new ArrayList<String>();
    	List<String> newValues = new ArrayList<String>();
    	
    	for (int i=0; i < onNames.length; i++) {
    		if (android.os.Build.VERSION.SDK_INT > 10 || (!onValues[i].equals("flipleft") && !onValues[i].equals("flipright"))) {
	    		newNames.add(onNames[i]);
	    		newValues.add(onValues[i]);
    		}
    	}
    	
    	/*
    	 * Add user defined keys to the awake list
    	 */
    	for (int i=0; i < keyArray.length; i++) {
    		if (!"".equals(keyArray[i])) {
	    		if (!newValues.contains(keyArray[i])) {
	    			newNames.add( Common.keycodeToString( Integer.parseInt(keyArray[i]) ) );
	        		newValues.add(keyArray[i]);
	    		}
    		}
    	}
    	
    	mOptionsListNames = getResources().getStringArray(R.array.remap_options_names);
    	mOptionsListValues = getResources().getStringArray(R.array.remap_options_values);
    	mRemapOnNames = newNames.toArray(new String[newNames.size()]);
    	mRemapOnValues = newValues.toArray(new String[newValues.size()]);
    	mRemapOffNames = getResources().getStringArray(R.array.remap_actions_off_names);
    	mRemapOffValues = getResources().getStringArray(R.array.remap_actions_off_values);
    	
    	mPrefRemoveButton = (Preference) findPreference("remove_button");
    	mPrefRemoveButton.setOnPreferenceClickListener(this);
    	
    	mPrefOptions = (ListPreference) findPreference("pref_options");
    	mPrefOptions.setOnPreferenceChangeListener(this);
    	mPrefOptions.setValue("awake");
    	
    	mCategSleep = (PreferenceCategory) findPreference("category_remap_sleep_actions");
    	getPreferenceScreen().removePreference(mCategSleep);
    	
    	mCategAwake = (PreferenceCategory) findPreference("category_remap_awake_actions");

    	mPrefOnEnaled = new CheckBoxPreference(this);
    	mPrefOnEnaled.setKey(Common.Remap.KEY_ON_ENABLED + mKeyCode);
    	mPrefOnEnaled.setPersistent(false);
    	mPrefOnEnaled.setTitle(R.string.preference_title_remap_enabled);
    	mPrefOnEnaled.setChecked(Common.Remap.isKeyEnabled(mKeyCode, false));
    	mCategAwake.addPreference(mPrefOnEnaled);
    	
    	mPrefOnClick = new ListPreference(this);
    	mPrefOnClick.setKey(Common.Remap.KEY_ON_ACTION_CLICK + mKeyCode);
    	mPrefOnClick.setPersistent(false);
    	mPrefOnClick.setTitle(R.string.preference_title_remap_click);
    	mPrefOnClick.setEntries(mRemapOnNames);
    	mPrefOnClick.setEntryValues(mRemapOnValues);
    	mPrefOnClick.setValue(Common.Remap.getKeyClick(mKeyCode, false));
    	mPrefOnClick.setOnPreferenceChangeListener(this);
    	mCategAwake.addPreference(mPrefOnClick);
    	
    	mPrefOnTap = new ListPreference(this);
    	mPrefOnTap.setKey(Common.Remap.KEY_ON_ACTION_TAP + mKeyCode);
    	mPrefOnTap.setPersistent(false);
    	mPrefOnTap.setTitle(R.string.preference_title_remap_tap);
    	mPrefOnTap.setEntries(mRemapOnNames);
    	mPrefOnTap.setEntryValues(mRemapOnValues);
    	mPrefOnTap.setValue(Common.Remap.getKeyTap(mKeyCode, false));
    	mPrefOnTap.setOnPreferenceChangeListener(this);
    	mCategAwake.addPreference(mPrefOnTap);
    	
    	mPrefOnPress = new ListPreference(this);
    	mPrefOnPress.setKey(Common.Remap.KEY_ON_ACTION_PRESS + mKeyCode);
    	mPrefOnPress.setPersistent(false);
    	mPrefOnPress.setTitle(R.string.preference_title_remap_press);
    	mPrefOnPress.setEntries(mRemapOnNames);
    	mPrefOnPress.setEntryValues(mRemapOnValues);
    	mPrefOnPress.setValue(Common.Remap.getKeyPress(mKeyCode, false));
    	mPrefOnPress.setOnPreferenceChangeListener(this);
    	mCategAwake.addPreference(mPrefOnPress);
    	
    	mPrefOffEnaled = new CheckBoxPreference(this);
    	mPrefOffEnaled.setKey(Common.Remap.KEY_OFF_ENABLED + mKeyCode);
    	mPrefOffEnaled.setPersistent(false);
    	mPrefOffEnaled.setTitle(R.string.preference_title_remap_enabled);
    	mPrefOffEnaled.setChecked(Common.Remap.isKeyEnabled(mKeyCode, true));
    	mCategSleep.addPreference(mPrefOffEnaled);
    	
    	mPrefOffClick = new ListPreference(this);
    	mPrefOffClick.setKey(Common.Remap.KEY_OFF_ACTION_CLICK + mKeyCode);
    	mPrefOffClick.setPersistent(false);
    	mPrefOffClick.setTitle(R.string.preference_title_remap_click);
    	mPrefOffClick.setEntries(mRemapOffNames);
    	mPrefOffClick.setEntryValues(mRemapOffValues);
    	mPrefOffClick.setValue(Common.Remap.getKeyClick(mKeyCode, true));
    	mPrefOffClick.setOnPreferenceChangeListener(this);
    	mCategSleep.addPreference(mPrefOffClick);
    	
    	mPrefOffTap = new ListPreference(this);
    	mPrefOffTap.setKey(Common.Remap.KEY_OFF_ACTION_TAP + mKeyCode);
    	mPrefOffTap.setPersistent(false);
    	mPrefOffTap.setTitle(R.string.preference_title_remap_tap);
    	mPrefOffTap.setEntries(mRemapOffNames);
    	mPrefOffTap.setEntryValues(mRemapOffValues);
    	mPrefOffTap.setValue(Common.Remap.getKeyTap(mKeyCode, true));
    	mPrefOffTap.setOnPreferenceChangeListener(this);
    	mCategSleep.addPreference(mPrefOffTap);
    	
    	mPrefOffPress = new ListPreference(this);
    	mPrefOffPress.setKey(Common.Remap.KEY_OFF_ACTION_PRESS + mKeyCode);
    	mPrefOffPress.setPersistent(false);
    	mPrefOffPress.setTitle(R.string.preference_title_remap_press);
    	mPrefOffPress.setEntries(mRemapOffNames);
    	mPrefOffPress.setEntryValues(mRemapOffValues);
    	mPrefOffPress.setValue(Common.Remap.getKeyPress(mKeyCode, true));
    	mPrefOffPress.setOnPreferenceChangeListener(this);
    	mCategSleep.addPreference(mPrefOffPress);
    	
    	Common.updateListSummary(mPrefOptions, mOptionsListValues, mOptionsListNames);
    	Common.updateListSummary(mPrefOnClick, mRemapOnValues, mRemapOnNames);
    	Common.updateListSummary(mPrefOnTap, mRemapOnValues, mRemapOnNames);
    	Common.updateListSummary(mPrefOnPress, mRemapOnValues, mRemapOnNames);
    	Common.updateListSummary(mPrefOffClick, mRemapOffValues, mRemapOffNames);
    	Common.updateListSummary(mPrefOffTap, mRemapOffValues, mRemapOffNames);
    	Common.updateListSummary(mPrefOffPress, mRemapOffValues, mRemapOffNames);
    	
    	handleEnabledState(mPrefOnEnaled);
    	handleEnabledState(mPrefOffEnaled);
	}
	
	@Override
	protected void onPause() {
		super.onPause();

		if (mUpdated) {
			Common.requestConfigUpdate(this);
		}
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference == mPrefRemoveButton) {
			setResult(mKeyCode);
			
			finish();
		}
		
		return false;
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object value) {
		if (preference == mPrefOptions) {
			PreferenceScreen screen = getPreferenceScreen();
			
			if (((String) value).equals("sleep")) {
				screen.removePreference(mCategAwake);
				screen.addPreference(mCategSleep);
				
				mPrefOptions.setValue("sleep");
				
			} else {
				screen.removePreference(mCategSleep);
				screen.addPreference(mCategAwake);
				
				mPrefOptions.setValue("awake");
			}
			
			Common.updateListSummary(mPrefOptions, mOptionsListValues, mOptionsListNames);
			
			return true;
			
		} else if (preference == mPrefOnClick || 
					preference == mPrefOnTap ||
					preference == mPrefOnPress || 
					preference == mPrefOffClick || 
					preference == mPrefOffTap || 
					preference == mPrefOffPress) {
			
			Common.getSharedPreferences(this).edit().putString(preference.getKey(), (String) value).apply();
			
			((ListPreference) preference).setValue((String) value);
			
			if (preference == mPrefOnClick || 
				preference == mPrefOnTap ||
				preference == mPrefOnPress) {
				
				Common.updateListSummary((ListPreference) preference, mRemapOnValues, mRemapOnNames);
				
			} else if (preference == mPrefOffClick || 
					preference == mPrefOffTap ||
					preference == mPrefOffPress) {
				
				Common.updateListSummary((ListPreference) preference, mRemapOffValues, mRemapOffNames);
			}
			
			mUpdated = true;
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		if (preference == mPrefOnEnaled || 
				preference == mPrefOffEnaled) {
			
			Common.getSharedPreferences(this).edit().putBoolean(preference.getKey(), ((CheckBoxPreference) preference).isChecked()).apply();
			
			handleEnabledState(preference);
			
			mUpdated = true;
			
			return true;
		}
		
		return false;
	}
	
	private void handleEnabledState(Preference preference) {
		if (preference == mPrefOnEnaled) {
			mPrefOnClick.setEnabled( mPrefOnEnaled.isChecked() );
			mPrefOnTap.setEnabled( mPrefOnEnaled.isChecked() );
			mPrefOnPress.setEnabled( mPrefOnEnaled.isChecked() );
			
		} else if (preference == mPrefOffEnaled) {
			mPrefOffClick.setEnabled( mPrefOffEnaled.isChecked() );
			mPrefOffTap.setEnabled( mPrefOffEnaled.isChecked() );
			mPrefOffPress.setEnabled( mPrefOffEnaled.isChecked() );
		}
	}
}
