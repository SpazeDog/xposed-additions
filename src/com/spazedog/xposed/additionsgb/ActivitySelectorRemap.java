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

import java.util.List;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import com.spazedog.xposed.additionsgb.Common.AppInfo;
import com.spazedog.xposed.additionsgb.Common.RemapAction;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference;
import com.spazedog.xposed.additionsgb.tools.views.WidgetPreference;

public class ActivitySelectorRemap extends PreferenceActivity implements OnPreferenceClickListener {
	
	private XServiceManager mPreferences;
	
	private Boolean mSetup = false;
	
	private String mAction;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.activity_selector_remap);
		
		mAction = getIntent().getStringExtra("action");
		
		if ("add_condition".equals(mAction)) {
			setTitle(R.string.preference_add_condition);
			
		} else if ("add_action".equals(mAction)) {
			setTitle(R.string.preference_add_action);
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
    		onBackPressed();
    		
    	} else {
    		setup();
    	}
    }
    
    @Override
    protected void onStop() {
    	super.onStop();
    	
    	mPreferences = null;
    }
    
    @Override
    public void onBackPressed() {
    	setResult(RESULT_CANCELED);
    	
    	finish();
    }
    
    private void setup() {
    	if (mSetup != (mSetup = true)) {
    		PreferenceScreen preferenceScreen = getPreferenceScreen();
    		
    		if ("add_condition".equals(mAction)) {
				preferenceScreen.removePreference(findPreference("custom_group"));
				preferenceScreen.addPreference(getSelectPreference(getResources().getString(R.string.condition_type_$on), null, "on", null, null));
				preferenceScreen.addPreference(getSelectPreference(getResources().getString(R.string.condition_type_$off), null, "off", null, null));
				preferenceScreen.addPreference(getSelectPreference(getResources().getString(R.string.condition_type_$guard), null, "guard", null, null));
				
    		} else if ("add_action".equals(mAction)) {
    			PreferenceCategory category = (PreferenceCategory) findPreference("custom_group");
    			String condition = getIntent().getStringExtra("condition");
    			
    			for (RemapAction value : RemapAction.VALUES) {
    				if (value.isValid(this, condition)) {
    					if (value.dispatch) {
    						preferenceScreen.addPreference(getSelectPreference(value.getLabel(this), getResources().getString(R.string.text_key, value.name), value.name, null, null));
    						
    					} else {
    						category.addPreference(getSelectPreference(value.getLabel(this), value.getDescription(this), value.name, null, null));
    					}
    				}
    			}
    		}
    		
			if (mPreferences.isPackageUnlocked() && !("add_action".equals(mAction) && "off".equals(getIntent().getStringExtra("condition")))) {
				findPreference("load_apps_preference").setOnPreferenceClickListener(this);
				
			} else {
				preferenceScreen.removePreference(findPreference("application_group"));
			}
    	}
    }
    
	protected AsyncTask<Object, Void, List<AppInfo>> loadApplicationList = new AsyncTask<Object, Void, List<AppInfo>>() {
		
		ProgressDialog mProgress;
		
		@Override
		protected void onPreExecute() {
			mProgress = new ProgressDialog(ActivitySelectorRemap.this);
			mProgress.setMessage(getResources().getString(R.string.task_applocation_list));
			mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgress.setCancelable(false);
			mProgress.setCanceledOnTouchOutside(false);
			mProgress.show();
		}

		@Override
		protected List<AppInfo> doInBackground(Object... args) {
			return Common.AppInfo.loadApplicationList((Context) args[0], mProgress);
		}
		
		@Override
		protected void onPostExecute(List<AppInfo> packages) {
			PreferenceCategory category = (PreferenceCategory) findPreference("application_group");
			
			for (int i=0; i < packages.size(); i++) {
				AppInfo appInfo = packages.get(i);
				category.addPreference(
						getSelectPreference(
								appInfo.getLabel(),
								appInfo.getName(),
								appInfo.getName(),
								appInfo.getIcon() != null ? appInfo.getIcon() : null,
								null
						)
				);
			}
			
			if (mProgress != null && mProgress.isShowing()) {
				try {
					mProgress.dismiss();
					
				} catch(Throwable e) {}
			}
		}
	};
	
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if ("load_apps_preference".equals(preference.getKey())) {
			PreferenceCategory category = (PreferenceCategory) findPreference("application_group");
			category.removePreference(preference);
			
			loadApplicationList.execute(this.getApplicationContext()); return true;
			
		} else {
			Intent intent = getIntent();
			intent.putExtra("result", (String) ((IWidgetPreference) preference).getTag());
			
			setResult(RESULT_OK, intent);
			
			finish();
		}
		
		return false;
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private Preference getSelectPreference(String title, String summary, String tag, Drawable icon, Intent intent) {
		WidgetPreference preference = new WidgetPreference(this);
		preference.setTitle(title);
		preference.setTag(tag);
		preference.setOnPreferenceClickListener(this);
		
		if (summary != null) {
			preference.setSummary(summary);
		}
		
		if (icon != null) {
			preference.setIcon(icon);
		}
		
		return preference;
	}
}
