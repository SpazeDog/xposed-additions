package com.spazedog.xposed.additionsgb;

import java.util.ArrayList;
import java.util.List;

import com.spazedog.xposed.additionsgb.DialogBroadcastReceiver.OnDialogListener;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

public class ActivityRemapButton extends PreferenceActivity implements OnPreferenceClickListener, OnPreferenceChangeListener {
	
	private Integer mKeyCurrent;
	private Integer mKeyPrimary;
	
	private Preference mPrefAddButton;
	private Preference mPrefRemoveButton;
	private ListPreference mPrefOptions;
	private PreferenceCategory mPrefButtons;
	
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
	
	private DialogBroadcastReceiver mDialog;
	
	private Boolean isUnlocked = false;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	isUnlocked = Common.isUnlocked(this);
    	
    	mKeyCurrent = getIntent().getIntExtra("keycode", 0);
    	mKeyPrimary = getIntent().getIntExtra("keyprimary", 0);

    	if (mKeyPrimary > 0) {
    		setTitle(
    				Common.keycodeToString(mKeyPrimary) + 
    				" + " + 
    				Common.keycodeToString( Common.extractKeyCode(mKeyPrimary, mKeyCurrent) )
    		);
    		
    	} else {
    		setTitle(Common.keycodeToString(mKeyCurrent));
    	}
    	
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
    	
    	mPrefAddButton = (Preference) findPreference("add_button");
    	if (isUnlocked && mKeyPrimary == 0) {
    		mPrefAddButton.setOnPreferenceClickListener(this);
    		
    	} else {
    		getPreferenceScreen().removePreference(mPrefAddButton);
    	}
    	
    	mPrefButtons = (PreferenceCategory) findPreference("category_secondary_buttons");
    	if (!isUnlocked || mKeyPrimary > 0) {
    		getPreferenceScreen().removePreference(mPrefButtons);
    		
    	} else {
        	Integer[] keyList = Common.Remap.getKeyList(mKeyCurrent);

        	if (keyList.length > 0) {
	        	for (int i=0; i < keyList.length; i++) {
	        		createKeyPreference(keyList[i]);
	        	}
	        	
        	} else {
        		getPreferenceScreen().removePreference(mPrefButtons);
        	}
    	}
    	
    	mPrefOptions = (ListPreference) findPreference("pref_options");
    	mPrefOptions.setOnPreferenceChangeListener(this);
    	mPrefOptions.setValue("awake");
    	
    	mCategSleep = (PreferenceCategory) findPreference("category_remap_sleep_actions");
    	getPreferenceScreen().removePreference(mCategSleep);
    	
    	mCategAwake = (PreferenceCategory) findPreference("category_remap_awake_actions");

    	mPrefOnEnaled = new CheckBoxPreference(this);
    	mPrefOnEnaled.setKey(Common.Remap.KEY_ON_ENABLED + mKeyCurrent);
    	mPrefOnEnaled.setPersistent(false);
    	mPrefOnEnaled.setTitle(R.string.preference_title_remap_enabled);
    	if (mKeyPrimary > 0 && !Common.Remap.isKeyEnabled(mKeyPrimary, false)) {
    		mPrefOnEnaled.setChecked(false);
    		mPrefOnEnaled.setEnabled(false);
    		
			Toast.makeText(this, R.string.message_primary_disabled_awake, Toast.LENGTH_SHORT).show();
    		
    	} else {
    		mPrefOnEnaled.setChecked(Common.Remap.isKeyEnabled(mKeyCurrent, false));
    	}
    	mCategAwake.addPreference(mPrefOnEnaled);
    	
    	mPrefOnClick = new ListPreference(this);
    	mPrefOnClick.setKey(Common.Remap.KEY_ON_ACTION_CLICK + mKeyCurrent);
    	mPrefOnClick.setPersistent(false);
    	mPrefOnClick.setTitle(R.string.preference_title_remap_click);
    	mPrefOnClick.setEntries(mRemapOnNames);
    	mPrefOnClick.setEntryValues(mRemapOnValues);
    	mPrefOnClick.setValue(Common.Remap.getKeyClick(mKeyCurrent, false));
    	mPrefOnClick.setOnPreferenceChangeListener(this);
    	mCategAwake.addPreference(mPrefOnClick);
    	
    	mPrefOnTap = new ListPreference(this);
    	mPrefOnTap.setKey(Common.Remap.KEY_ON_ACTION_TAP + mKeyCurrent);
    	mPrefOnTap.setPersistent(false);
    	mPrefOnTap.setTitle(R.string.preference_title_remap_tap);
    	mPrefOnTap.setEntries(mRemapOnNames);
    	mPrefOnTap.setEntryValues(mRemapOnValues);
    	mPrefOnTap.setValue(Common.Remap.getKeyTap(mKeyCurrent, false));
    	mPrefOnTap.setOnPreferenceChangeListener(this);
    	if (isUnlocked) {
    		mCategAwake.addPreference(mPrefOnTap);
    	}
    	
    	mPrefOnPress = new ListPreference(this);
    	mPrefOnPress.setKey(Common.Remap.KEY_ON_ACTION_PRESS + mKeyCurrent);
    	mPrefOnPress.setPersistent(false);
    	mPrefOnPress.setTitle(R.string.preference_title_remap_press);
    	mPrefOnPress.setEntries(mRemapOnNames);
    	mPrefOnPress.setEntryValues(mRemapOnValues);
    	mPrefOnPress.setValue(Common.Remap.getKeyPress(mKeyCurrent, false));
    	mPrefOnPress.setOnPreferenceChangeListener(this);
    	mCategAwake.addPreference(mPrefOnPress);
    	
    	mPrefOffEnaled = new CheckBoxPreference(this);
    	mPrefOffEnaled.setKey(Common.Remap.KEY_OFF_ENABLED + mKeyCurrent);
    	mPrefOffEnaled.setPersistent(false);
    	mPrefOffEnaled.setTitle(R.string.preference_title_remap_enabled);
    	if (mKeyPrimary > 0 && !Common.Remap.isKeyEnabled(mKeyPrimary, true)) {
    		mPrefOffEnaled.setChecked(false);
    		mPrefOffEnaled.setEnabled(false);
    		
    	} else {
    		mPrefOffEnaled.setChecked(Common.Remap.isKeyEnabled(mKeyCurrent, true));
    	}
    	mCategSleep.addPreference(mPrefOffEnaled);
    	
    	mPrefOffClick = new ListPreference(this);
    	mPrefOffClick.setKey(Common.Remap.KEY_OFF_ACTION_CLICK + mKeyCurrent);
    	mPrefOffClick.setPersistent(false);
    	mPrefOffClick.setTitle(R.string.preference_title_remap_click);
    	mPrefOffClick.setEntries(mRemapOffNames);
    	mPrefOffClick.setEntryValues(mRemapOffValues);
    	mPrefOffClick.setValue(Common.Remap.getKeyClick(mKeyCurrent, true));
    	mPrefOffClick.setOnPreferenceChangeListener(this);
    	mCategSleep.addPreference(mPrefOffClick);
    	
    	mPrefOffTap = new ListPreference(this);
    	mPrefOffTap.setKey(Common.Remap.KEY_OFF_ACTION_TAP + mKeyCurrent);
    	mPrefOffTap.setPersistent(false);
    	mPrefOffTap.setTitle(R.string.preference_title_remap_tap);
    	mPrefOffTap.setEntries(mRemapOffNames);
    	mPrefOffTap.setEntryValues(mRemapOffValues);
    	mPrefOffTap.setValue(Common.Remap.getKeyTap(mKeyCurrent, true));
    	mPrefOffTap.setOnPreferenceChangeListener(this);
    	if (isUnlocked) {
    		mCategSleep.addPreference(mPrefOffTap);
    	}
    	
    	mPrefOffPress = new ListPreference(this);
    	mPrefOffPress.setKey(Common.Remap.KEY_OFF_ACTION_PRESS + mKeyCurrent);
    	mPrefOffPress.setPersistent(false);
    	mPrefOffPress.setTitle(R.string.preference_title_remap_press);
    	mPrefOffPress.setEntries(mRemapOffNames);
    	mPrefOffPress.setEntryValues(mRemapOffValues);
    	mPrefOffPress.setValue(Common.Remap.getKeyPress(mKeyCurrent, true));
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
	protected void onResume() {
		super.onResume();
		
		if (mDialog != null && mDialog.getDialog() != null && mDialog.getDialog().isShowing()) {
			requestInterceptStart();
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		if (mDialog != null && mDialog.getDialog() != null && mDialog.getDialog().isShowing()) {
			requestInterceptStop();
		}

		if (mUpdated) {
			Common.requestConfigUpdate(this);
			mUpdated = false;
		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		
		if (mDialog != null) {
			mDialog.destroy();
			mDialog = null;
		}
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference == mPrefRemoveButton) {
			setResult(mKeyCurrent);
			
			finish();
			
		} else if (preference == mPrefAddButton) {
			mDialog = new DialogBroadcastReceiver(this, R.layout.dialog_intercept_key);
			mDialog.setTitle(R.string.alert_dialog_title_intercept_key);
			mDialog.setBroadcastIntent(new IntentFilter(Common.BroadcastOptions.INTENT_ACTION_RESPONSE));
			mDialog.setOnDialogListener(new OnDialogListener(){
				
				private Integer mKeySecondaryCode;
				private String mKeyText;
				
				@Override
				public void OnClose(DialogBroadcastReceiver dialog, Boolean positive) {
					requestInterceptStop();
					
					dialog.destroy();
					dialog = null;
					
					if (positive) {
						if (mKeySecondaryCode != null && mKeySecondaryCode > 0) {
							int keyCode = Common.generateKeyCode(mKeyCurrent, mKeySecondaryCode);
							SharedPreferences sharedPreferences = Common.getSharedPreferences(ActivityRemapButton.this);
							String[] keyArray = sharedPreferences.getString(Common.Remap.KEY_COLLECTION_SECONDARY+mKeyCurrent, "").split(",");
							List<String> keyList = new ArrayList<String>();
							
							for (int i=0; i < keyArray.length; i++) {
								if (keyArray[i] != null && keyArray[i].matches("^[0-9]+$")) {
									keyList.add(keyArray[i]);
								}
							}
							
							if (!keyList.contains(keyCode)) {
								keyList.add("" + keyCode);
								
								sharedPreferences.edit().putString(Common.Remap.KEY_COLLECTION_SECONDARY+mKeyCurrent, TextUtils.join(",", keyList)).apply();
								
								createKeyPreference(keyCode);
								
							} else {
								Toast.makeText(ActivityRemapButton.this, R.string.alert_dialog_title_key_exists, Toast.LENGTH_LONG).show();
							}
						}
					}
				}
	
				@Override
				public void OnOpen(DialogBroadcastReceiver dialog) {
					if (mDialog == null) {
						mDialog = dialog;
					}
					
					mKeyText = mKeyCurrent + " (" + Common.keycodeToString(mKeyCurrent) + ")";
					
					((TextView) mDialog.getDialog().findViewById(R.id.content_name)).setText("Key Code");
					((TextView) mDialog.getDialog().findViewById(R.id.content_text)).setText("Press the key that you would like to add");
					
					dialog.bind();
					
					requestInterceptStart();
				}
	
				@Override
				public void OnReceive(DialogBroadcastReceiver dialog, Intent intent) {
					if (intent.hasExtra("response")) {
						int key = intent.getIntExtra("response", 0);
						
						if (key != 0 && key != mKeyCurrent) {
							mKeySecondaryCode = key;
									
							((TextView) mDialog.getDialog().findViewById(R.id.content_value)).setText("" + mKeyText + " + " + mKeySecondaryCode + " (" + Common.keycodeToString(mKeySecondaryCode) + ")");
						}
					}
				}
			});
			
			mDialog.open();
		
			return true;
			
		} else if (preference.getIntent() != null) {
			startActivityForResult(preference.getIntent(), preference.getIntent().getIntExtra("keycode", 0));
			
			return true;
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
				
				if (mKeyPrimary > 0 && !Common.Remap.isKeyEnabled(mKeyPrimary, true)) {
					Toast.makeText(this, R.string.message_primary_disabled_sleep, Toast.LENGTH_SHORT).show();
				}
				
			} else {
				screen.removePreference(mCategSleep);
				screen.addPreference(mCategAwake);
				
				mPrefOptions.setValue("awake");
				
				if (mKeyPrimary > 0 && !Common.Remap.isKeyEnabled(mKeyPrimary, false)) {
					Toast.makeText(this, R.string.message_primary_disabled_awake, Toast.LENGTH_SHORT).show();
				}
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
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode > 0) {
			String keyCode = "" + resultCode;
			SharedPreferences sharedPreferences = Common.getSharedPreferences(ActivityRemapButton.this);
			String[] keyArray = sharedPreferences.getString(Common.Remap.KEY_COLLECTION_SECONDARY + mKeyCurrent, "").split(",");
			List<String> keyList = new ArrayList<String>();
			
			for (int i=0; i < keyArray.length; i++) {
				if (!"".equals(keyArray[i])) {
					keyList.add(keyArray[i]);
				}
			}
			
			if (keyList.contains(keyCode)) {
				keyList.remove(keyCode);
				
				Editor editor = sharedPreferences.edit();
				
				editor.putString(Common.Remap.KEY_COLLECTION_SECONDARY + mKeyCurrent, TextUtils.join(",", keyList));
				editor.remove(Common.Remap.KEY_COLLECTION_SECONDARY + keyCode);
				editor.remove(Common.Remap.KEY_OFF_ENABLED + keyCode);
				editor.remove(Common.Remap.KEY_OFF_ACTION_CLICK + keyCode);
				editor.remove(Common.Remap.KEY_OFF_ACTION_TAP + keyCode);
				editor.remove(Common.Remap.KEY_OFF_ACTION_PRESS + keyCode);
				editor.remove(Common.Remap.KEY_ON_ENABLED + keyCode);
				editor.remove(Common.Remap.KEY_ON_ACTION_CLICK + keyCode);
				editor.remove(Common.Remap.KEY_ON_ACTION_TAP + keyCode);
				editor.remove(Common.Remap.KEY_ON_ACTION_PRESS + keyCode);
				
				editor.apply();
				
				mUpdated = true;
				
				mPrefButtons.removePreference( findPreference("keycode/" + keyCode) );
				
		    	if (mPrefButtons.getPreferenceCount() == 0) {
		    		getPreferenceScreen().removePreference(mPrefButtons);
		    	}
				
				Toast.makeText(this, R.string.key_removed, Toast.LENGTH_LONG).show();
			}
		}
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
	
	protected void createKeyPreference(Integer keyCode) {
    	Intent intent = new Intent();
    	intent.setAction( Intent.ACTION_VIEW );
    	intent.setClass(this, this.getClass());
    	intent.putExtra("keycode", (int) keyCode);
    	intent.putExtra("keyprimary", (int) mKeyCurrent);
    	
    	int keySecondary = Common.extractKeyCode(mKeyCurrent, keyCode);
    	
    	Preference buttonPreference = new Preference(this);
    	buttonPreference.setKey("keycode/" + keyCode);
    	buttonPreference.setTitle(Common.keycodeToString(mKeyCurrent) + " + " + Common.keycodeToString(keySecondary));
    	buttonPreference.setSummary("Key Code " + mKeyCurrent + "+" + keySecondary);
    	buttonPreference.setIntent(intent);
    	buttonPreference.setOnPreferenceClickListener(this);
    	
    	if (mPrefButtons.getPreferenceCount() == 0) {
    		getPreferenceScreen().addPreference(mPrefButtons);
    	}

    	mPrefButtons.addPreference(buttonPreference);
	}
	
	protected void requestInterceptStart() {
		Intent intent = new Intent(Common.BroadcastOptions.INTENT_ACTION_REQUEST);
		intent.putExtra("request", Common.BroadcastOptions.REQUEST_ENABLE_KEYCODE_INTERCEPT);
		
		sendBroadcast(intent);
	}
	
	protected void requestInterceptStop() {
		Intent intent = new Intent(Common.BroadcastOptions.INTENT_ACTION_REQUEST);
		intent.putExtra("request", Common.BroadcastOptions.REQUEST_DISABLE_KEYCODE_INTERCEPT);
		
		sendBroadcast(intent);
	}
}
