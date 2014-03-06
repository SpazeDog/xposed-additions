package com.spazedog.xposed.additionsgb;

import java.util.ArrayList;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.view.View;
import android.widget.CheckBox;

import com.spazedog.xposed.additionsgb.Common.Index;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference;
import com.spazedog.xposed.additionsgb.tools.views.WidgetPreference;
import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference.OnWidgetBindListener;
import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference.OnWidgetClickListener;

public class ActivityScreenRemapCondition extends PreferenceActivity implements OnPreferenceClickListener, OnWidgetClickListener, OnWidgetBindListener {
	
	private XServiceManager mPreferences;
	
	private Boolean mSetup = false;
	
	private String mKey;
	private String mCondition;
	
	private ArrayList<String> mKeyActions = new ArrayList<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.activity_screen_remap_condition);
		
		mKey = getIntent().getStringExtra("key");
		mCondition = getIntent().getStringExtra("condition");
		
		if (mKey == null || mCondition == null) {
			finish();
			
		} else {
			setTitle( Common.conditionToString(this, mCondition) );
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
    	}
    }
    
    @Override
    protected void onStop() {
    	super.onStop();
    	
    	mPreferences.commit();
    	mPreferences = null;
    }
    
    private void setup() {
    	if (mSetup != (mSetup = true)) {
			mKeyActions = (ArrayList<String>) mPreferences.getStringArrayGroup(String.format(Index.array.groupKey.remapKeyActions_$, mCondition), mKey, new ArrayList<String>());
			
			String condition = Common.getConditionIdentifier(this, mCondition) > 0 ? mCondition : "on";
			
			if (mKeyActions.size() < 3) {
				Integer count = mKeyActions.size();
				
				if (count == 0) mKeyActions.add(null);
				if (count < 2) mKeyActions.add(null);
				if (count < 3) mKeyActions.add(null);
			}
			
			WidgetPreference clickPreference = (WidgetPreference) findPreference("state_click_preference");
			clickPreference.setSummary( mKeyActions.get(0) != null ? Common.actionToString(this, mKeyActions.get(0)) : "" );
			clickPreference.setOnWidgetBindListener(this);
			clickPreference.setOnWidgetClickListener(this);
			clickPreference.setOnPreferenceClickListener(this);
			clickPreference.setIntent( 
					new Intent(this, ActivitySelectorRemap.class)
					.putExtra("action", "add_action")
					.putExtra("index", 0)
					.putExtra("condition", condition)
			);
			
			if (mPreferences.isPackageUnlocked()) {
    			WidgetPreference tapPreference = (WidgetPreference) findPreference("state_tap_preference");
    			tapPreference.setSummary( mKeyActions.get(1) != null ? Common.actionToString(this, mKeyActions.get(1)) : "" );
    			tapPreference.setOnWidgetBindListener(this);
    			tapPreference.setOnWidgetClickListener(this);
    			tapPreference.setOnPreferenceClickListener(this);
    			tapPreference.setIntent( 
    					new Intent(this, ActivitySelectorRemap.class)
    					.putExtra("action", "add_action")
    					.putExtra("index", 1)
    					.putExtra("condition", condition)
    			);
    			
			} else {
				((PreferenceCategory) findPreference("actions_group")).removePreference(findPreference("state_tap_preference"));
			}
			
			WidgetPreference pressPreference = (WidgetPreference) findPreference("state_press_preference");
			pressPreference.setSummary( mKeyActions.get(2) != null ? Common.actionToString(this, mKeyActions.get(2)) : "" );
			pressPreference.setOnWidgetBindListener(this);
			pressPreference.setOnWidgetClickListener(this);
			pressPreference.setOnPreferenceClickListener(this);
			pressPreference.setIntent( 
					new Intent(this, ActivitySelectorRemap.class)
					.putExtra("action", "add_action")
					.putExtra("index", 2)
					.putExtra("condition", condition)
			);
    	}
    }
    
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference.getIntent() != null) {
			startActivityForResult(preference.getIntent(), 2); return true;
		}
		
		return false;
	}
	
	@Override
	public void onWidgetClick(Preference preference, View widgetView) {
		if (preference.getKey().startsWith("state_")) {
			String keyName = preference.getKey();
			Integer index = keyName.equals("state_click_preference") ? 0 : 
				keyName.equals("state_tap_preference") ? 1 : 2;
			
			CheckBox checbox = (CheckBox) widgetView.findViewById(android.R.id.checkbox);
			checbox.setChecked( !checbox.isChecked() );
			
			((IWidgetPreference) preference).setPreferenceEnabled(checbox.isChecked());

			mKeyActions.set(index, checbox.isChecked() ? "disabled" : null);
			mPreferences.putStringArrayGroup(String.format(Index.array.groupKey.remapKeyActions_$, mCondition), mKey, mKeyActions, true);

			preference.setSummary( mKeyActions.get(index) != null ? Common.actionToString(this, mKeyActions.get(index)) : "" );
		}
	}
	
	@Override
	public void onWidgetBind(Preference preference, View widgetView) {
		String keyName = preference.getKey();
		Integer index = keyName.equals("state_click_preference") ? 0 : 
			keyName.equals("state_tap_preference") ? 1 : 2;
		
		CheckBox checbox = (CheckBox) widgetView.findViewById(android.R.id.checkbox);
		checbox.setChecked( mKeyActions.get(index) != null );
		
		((IWidgetPreference) preference).setPreferenceEnabled(checbox.isChecked());
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		/*
		 * onActivityResult is called before anything else, 
		 * so this has not yet been re-initiated after onStop
		 */
		mPreferences = XServiceManager.getInstance();
		
		if (resultCode == RESULT_OK && "add_action".equals(data.getStringExtra("action"))) {
			String action = data.getStringExtra("result");
			Integer index = data.getIntExtra("index", 0);
			String keyName = index == 0 ? "state_click_preference" : 
				index == 1 ? "state_tap_preference" : "state_press_preference";
			
			mKeyActions.set(index, action);
			mPreferences.putStringArrayGroup(String.format(Index.array.groupKey.remapKeyActions_$, mCondition), mKey, mKeyActions, true);
			
			findPreference(keyName).setSummary( mKeyActions.get(index) != null ? Common.actionToString(this, mKeyActions.get(index)) : "" );
		}
	}
}
