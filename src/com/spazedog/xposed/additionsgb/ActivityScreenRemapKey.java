package com.spazedog.xposed.additionsgb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.view.View;

import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Settings;
import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference;
import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference.OnWidgetClickListener;
import com.spazedog.xposed.additionsgb.tools.views.WidgetPreference;

public class ActivityScreenRemapKey extends PreferenceActivity implements OnPreferenceClickListener, OnWidgetClickListener {
	
	private XServiceManager mPreferences;
	
	private Boolean mSetup = false;
	
	private String mKey;
	
	private String mKeyCode;
	
	private Integer mConditionOrder = 0;
	
	private Map<String, Integer> mConditionIndentifier = new HashMap<String, Integer>();
	
	private ArrayList<String> mForcedHapticKeys = new ArrayList<String>();
	private ArrayList<String> mKeyConditions = new ArrayList<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.activity_screen_remap_key);
		
		mKey = getIntent().getStringExtra("key");
		
		if (mKey == null) {
			finish();
			
		} else {
			setTitle( Common.keyToString(mKey) );
		}
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
    		if (!mKey.endsWith(":0")) {
    			((PreferenceCategory) findPreference("settings_group")).removePreference(findPreference("haptic_forced_preference"));
    			
    		} else {
    			mKeyCode = mKey.substring(0, mKey.indexOf(":"));
    			mForcedHapticKeys = (ArrayList<String>) mPreferences.getStringArray(Settings.REMAP_LIST_FORCED_HAPTIC, new ArrayList<String>());
    					
    			CheckBoxPreference hapticForced = (CheckBoxPreference) findPreference("haptic_forced_preference");
    			hapticForced.setChecked( mForcedHapticKeys.contains(mKeyCode) );
    			hapticForced.setOnPreferenceClickListener(this);
    		}
    		
			CheckBoxPreference callButton = (CheckBoxPreference) findPreference("call_button_preference");
			callButton.setChecked(mPreferences.getBooleanGroup(Settings.REMAP_KEY_ENABLE_CALLBTN, mKey));
			callButton.setOnPreferenceClickListener(this);
    		
			WidgetPreference addConditionPreference = (WidgetPreference) findPreference("add_condition_preference");
			addConditionPreference.setOnPreferenceClickListener(this);
			addConditionPreference.setIntent( 
					new Intent(this, ActivitySelectorRemap.class)
					.putExtra("action", "add_condition")
			);
			
			mKeyConditions = (ArrayList<String>) mPreferences.getStringArrayGroup(Settings.REMAP_KEY_LIST_CONDITIONS, mKey, new ArrayList<String>());
			for (String key : mKeyConditions) {
				addConditionPreference(key);
			}
    	}
    }
    
    private void update() {
		PreferenceCategory conditionCategory = ((PreferenceCategory) findPreference("conditions_group"));
		
		for (int i=0; i < conditionCategory.getPreferenceCount(); i++) {
			Preference conditionPreference = conditionCategory.getPreference(i);
			
			if ("condition_preference".equals(conditionPreference.getKey())) {
				setConditionSummary( conditionPreference );
			}
		}
    }
    
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference.getKey().equals("add_condition_preference")) {
			startActivityForResult(preference.getIntent(), 1); return true;
			
		} else if (preference.getKey().equals("haptic_forced_preference")) {
			Boolean isChecked = ((CheckBoxPreference) preference).isChecked();
			
			if (isChecked) {
				mForcedHapticKeys.add(mKeyCode);
				
			} else {
				mForcedHapticKeys.remove(mKeyCode);
			}
			
			mPreferences.putStringArray(Settings.REMAP_LIST_FORCED_HAPTIC, mForcedHapticKeys, true);
			
			return true;
			
		} else if (preference.getKey().equals("call_button_preference")) {
			Boolean isChecked = ((CheckBoxPreference) preference).isChecked();
			
			mPreferences.putBooleanGroup(Settings.REMAP_KEY_ENABLE_CALLBTN, mKey, isChecked, true);
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public void onWidgetClick(Preference preference, View widgetView) {
		if (preference.getKey().equals("condition_preference")) {
			String tag = (String) ((IWidgetPreference) preference).getTag();
			
			mKeyConditions.remove(tag);
			mPreferences.putStringArrayGroup(Settings.REMAP_KEY_LIST_CONDITIONS, mKey, mKeyConditions, true);
			mPreferences.removeGroup(Settings.REMAP_KEY_LIST_ACTIONS.get(tag), mKey);
			
			((PreferenceCategory) findPreference("conditions_group")).removePreference(preference);
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		/*
		 * onActivityResult is called before anything else, 
		 * so this has not yet been re-initiated after onStop
		 */
		mPreferences = XServiceManager.getInstance();
		
		if (resultCode == RESULT_OK && "add_condition".equals(data.getStringExtra("action"))) {
			String condition = data.getStringExtra("result");

			if (!mKeyConditions.contains(condition)) {
				mKeyConditions.add(condition);
				mPreferences.putStringArrayGroup(Settings.REMAP_KEY_LIST_CONDITIONS, mKey, mKeyConditions, true);
				
				addConditionPreference(condition);
			}
		}
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void addConditionPreference(String condition) {
		Integer identifier = Common.getConditionIdentifier(this, condition);
		Boolean enabled = mPreferences.isPackageUnlocked() || 
				identifier > 0;

		mConditionOrder += 1;
		
		WidgetPreference preference = new WidgetPreference(this);
		preference.setPreferenceEnabled(enabled);
		preference.setOrder(identifier > 0 ? 1 : mConditionOrder+1);
		preference.setKey("condition_preference");
		preference.setTag(condition);
		preference.setTitle( Common.conditionToString(this, condition) );
		preference.setWidgetLayoutResource(R.layout.widget_delete);
		preference.setOnWidgetClickListener(this);
		preference.setIntent( 
				new Intent(Intent.ACTION_VIEW)
				.setClass(this, ActivityScreenRemapCondition.class)
				.putExtra("key", mKey)
				.putExtra("condition", condition)
		);
		
		if (identifier == 0 && android.os.Build.VERSION.SDK_INT >= 11) {
			try {
				Drawable icon = getPackageManager().getApplicationIcon(condition);
				
				preference.setIcon(icon);
				
			} catch (NameNotFoundException e) {}
		}
		
		((PreferenceCategory) findPreference("conditions_group")).addPreference(preference);
	}
	
	private void setConditionSummary(Preference preference) {
		String condition = (String) ((IWidgetPreference) preference).getTag();
		Integer actionCount = 0;
		Boolean enabled = mPreferences.isPackageUnlocked() || 
				Common.getConditionIdentifier(this, condition) > 0;
				
		if (enabled) {
			List<String> actions = mPreferences.getStringArrayGroup(Settings.REMAP_KEY_LIST_ACTIONS.get(condition), mKey, new ArrayList<String>(3));
			for (String action : actions) {
				if (action != null) {
					actionCount += 1;
				}
			}
		}
		
		preference.setSummary( getResources().getString(Common.getQuantityResource(getResources(), "preference_action_count", actionCount), actionCount) );
	}
}
