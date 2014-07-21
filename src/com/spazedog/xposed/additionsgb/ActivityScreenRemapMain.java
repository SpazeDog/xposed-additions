package com.spazedog.xposed.additionsgb;

import java.util.ArrayList;
import java.util.List;

import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;
import android.widget.Toast;

import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Settings;
import com.spazedog.xposed.additionsgb.tools.DialogBroadcastReceiver;
import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference;
import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference.OnWidgetClickListener;
import com.spazedog.xposed.additionsgb.tools.views.SeekBarPreference;
import com.spazedog.xposed.additionsgb.tools.views.WidgetListPreference;
import com.spazedog.xposed.additionsgb.tools.views.WidgetPreference;

public class ActivityScreenRemapMain extends PreferenceActivity implements OnPreferenceChangeListener, OnPreferenceClickListener, OnWidgetClickListener {
	
	private XServiceManager mPreferences;
	
	private Boolean mSetup = false;
	
	private ArrayList<String> mKeyList = new ArrayList<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.activity_screen_remap_main);
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
    		finish();
    		
    	} else {
    		setup();
    		update();
    		
    		if (mDialog.isOpen()) {
    			mDialog.bind();
    		}
    	}
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    
		if (mDialog.isOpen()) {
			mDialog.unbind();
		}
    }
    
    @Override
    protected void onStop() {
    	super.onStop();
    	
    	if (mPreferences != null)
    		mPreferences.commit();
    	
    	mPreferences = null;
    }
    
    private void setup() {
    	if (mSetup != (mSetup = true)) {
			if (mPreferences.isPackageUnlocked()) {
				SeekBarPreference tapDelayPreference = (SeekBarPreference) findPreference("delay_key_tap_preference");
    			tapDelayPreference.setValue(mPreferences.getInt(Settings.REMAP_TIMEOUT_DOUBLECLICK, ViewConfiguration.getDoubleTapTimeout()) );
    			tapDelayPreference.setOnPreferenceChangeListener(this);
    			
			} else {
				((PreferenceCategory) findPreference("settings_group")).removePreference(findPreference("delay_key_tap_preference"));
			}
			
			SeekBarPreference pressDelayPreference = (SeekBarPreference) findPreference("delay_key_press_preference");
			pressDelayPreference.setValue(mPreferences.getInt(Settings.REMAP_TIMEOUT_LONGPRESS, ViewConfiguration.getLongPressTimeout()) );
			pressDelayPreference.setOnPreferenceChangeListener(this);
			
			WidgetPreference addKeyPreference = (WidgetPreference) findPreference("add_key_preference");
			addKeyPreference.setOnPreferenceClickListener(this);
			
			CheckBoxPreference allowExternalsPreference = (CheckBoxPreference) findPreference("allow_externals_preference");
			allowExternalsPreference.setOnPreferenceClickListener(this);
			allowExternalsPreference.setChecked(mPreferences.getBoolean(Settings.REMAP_ALLOW_EXTERNALS));
			
			mKeyList = (ArrayList<String>) mPreferences.getStringArray(Settings.REMAP_LIST_KEYS, new ArrayList<String>());
			for (String key : mKeyList) {
				addKeyPreference(key);
			}
    	}
    }
    
    private void update() {
		PreferenceCategory keyCategory = ((PreferenceCategory) findPreference("keys_group"));
		
		for (int i=0; i < keyCategory.getPreferenceCount(); i++) {
			Preference keyPreference = keyCategory.getPreference(i);
			
			if ("key_preference".equals(keyPreference.getKey())) {
				setKeySummary( keyPreference );
			}
		}
    }
    
	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference instanceof SeekBarPreference) {
			SeekBarPreference listPreference = (SeekBarPreference) preference;
			
			String configKey = listPreference.getKey().equals("delay_key_tap_preference") ? 
					Settings.REMAP_TIMEOUT_DOUBLECLICK : Settings.REMAP_TIMEOUT_LONGPRESS;
			
			mPreferences.putInt(configKey, (Integer) newValue, true);
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference.getKey().equals("add_key_preference")) {
			mDialog.open(this, R.layout.dialog_intercept_key); return true;
			
		} else if (preference.getKey().equals("allow_externals_preference")) {
			Boolean value = ((CheckBoxPreference) preference).isChecked();
			
			mPreferences.putBoolean(Settings.REMAP_ALLOW_EXTERNALS, value);
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public void onWidgetClick(Preference preference, View widgetView) {
		if (preference.getKey().equals("key_preference")) {
			String key = (String) ((IWidgetPreference) preference).getTag();
			
			mKeyList.remove(key);
			mPreferences.putStringArray(Settings.REMAP_LIST_KEYS, mKeyList, true);
			mPreferences.removeGroup(null, key);
			
			if (key.endsWith(":0")) {
				String keyCode = key.substring(0, key.indexOf(":"));
				ArrayList<String> forcedKeys = (ArrayList<String>) mPreferences.getStringArray(Settings.REMAP_LIST_FORCED_HAPTIC, new ArrayList<String>());
				
				forcedKeys.remove(keyCode);
				mPreferences.putStringArray(Settings.REMAP_LIST_FORCED_HAPTIC, forcedKeys, true);
			}
			
			((PreferenceCategory) findPreference("keys_group")).removePreference(preference);
		}
	}
	
	private void addKeyPreference(String key) {
		Boolean enabled = mPreferences.isPackageUnlocked() || key.endsWith(":0");
		
		WidgetPreference preference = new WidgetPreference(this);
		preference.setPreferenceEnabled(enabled);
		preference.setKey("key_preference");
		preference.setTag(key);
		preference.setTitle( Common.keyToString(key) );
		preference.setWidgetLayoutResource(R.layout.widget_delete);
		preference.setOnWidgetClickListener(this);
		preference.setIntent( 
				new Intent(Intent.ACTION_VIEW)
				.setClass(this, ActivityScreenRemapKey.class)
				.putExtra("key", key)
		);
		
		((PreferenceCategory) findPreference("keys_group")).addPreference(preference);
	}
	
	private void setKeySummary(Preference preference) {
		String key = (String) ((IWidgetPreference) preference).getTag();
		List<String> conditionList = mPreferences.getStringArrayGroup(Settings.REMAP_KEY_LIST_CONDITIONS, key, null);
		Integer conditionCount = conditionList == null ? 0 : conditionList.size();
		
		preference.setSummary( getResources().getString(Common.getQuantityResource(getResources(), "preference_condition_count", conditionCount), conditionCount) );
	}
	
	private DialogBroadcastReceiver mDialog = new DialogBroadcastReceiver() {
		private String mNewKey;
		private TextView valueView;
		private TextView textView;
		
		@Override
		protected void onOpen() {
			mNewKey = null;
			valueView = (TextView) getWindow().findViewById(R.id.content_value);
			textView = (TextView) getWindow().findViewById(R.id.content_text);
			
			getWindow().setTitle(R.string.alert_dialog_title_intercept_key);
		}
		
		@Override
		protected void onReceive(String action, Bundle data) {
			if (action.equals("keyIntercepter:keyCode")) {
				String intercepted = "" + data.get("keyCode");

				if (mNewKey != null && !mNewKey.contains(":") && mPreferences.isPackageUnlocked() && !intercepted.equals(mNewKey)) {
					mNewKey = mNewKey + ":" + intercepted;
					textView.setText(R.string.alert_dialog_summary_intercept_key);
					
				} else {
					mNewKey = intercepted;
					
					if (mPreferences.isPackageUnlocked()) {
						textView.setText(R.string.alert_dialog_summary_intercept_key_second);
					}
				}
				
				valueView.setText( Common.keyToString(mNewKey) );
			}
		}
		
		@Override
		protected void onClose(Boolean positive) {
			valueView = null;
			textView = null;
			
			if (positive && mNewKey != null) {
				String key = mNewKey.contains(":") ? mNewKey : mNewKey + ":0";
				
				if (!mKeyList.contains(key)) {
					mKeyList.add(key);
					mPreferences.putStringArray(Settings.REMAP_LIST_KEYS, mKeyList, true);
					
					addKeyPreference(key);
					
				} else {
					Toast.makeText(ActivityScreenRemapMain.this, R.string.alert_dialog_title_key_exists, Toast.LENGTH_LONG).show();
				}
			}
		}
		
		@Override
		protected void onBind() {
			mPreferences.sendBroadcast("keyIntercepter:enable", null);
		}
		
		@Override
		protected void onUnbind() {
			mPreferences.sendBroadcast("keyIntercepter:disable", null);
		}
	};
}
