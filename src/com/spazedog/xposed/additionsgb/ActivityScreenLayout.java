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

import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import com.spazedog.xposed.additionsgb.Common.AppInfo;
import com.spazedog.xposed.additionsgb.Common.Index;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;

public class ActivityScreenLayout extends PreferenceActivity implements OnPreferenceClickListener {
	
	private XServiceManager mPreferences;
	
	private Boolean mSetup = false;
	
	private PreferenceCategory mBlacklistCategory;
	private CheckBoxPreference mRotationPreference;
	
	private ArrayList<String> mBlacklist = new ArrayList<String>();
	
	private Boolean mUpdateBlacklist = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.activity_screen_layout);
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
    protected void onPause() {
    	super.onPause();
    	
    	if (mUpdateBlacklist && mPreferences != null) {
    		mPreferences.putStringArray(Index.array.key.layoutRotationBlacklist, mBlacklist, true);
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
    		mRotationPreference = (CheckBoxPreference) findPreference("rotation_preference");
    		mRotationPreference.setChecked( mPreferences.getBoolean(Index.bool.key.layoutRotationSwitch, Index.bool.value.layoutRotationSwitch) );
    		
    		if (mPreferences.isPackageUnlocked()) {
    			mBlacklistCategory = (PreferenceCategory) findPreference("app_blacklist_group");
    			mBlacklistCategory.setEnabled( mRotationPreference.isChecked() );
    			
    			mBlacklist = (ArrayList<String>) mPreferences.getStringArray(Index.array.key.layoutRotationBlacklist, Index.array.value.layoutRotationBlacklist);
    			
    			findPreference("load_apps_preference").setOnPreferenceClickListener(this);
    			
    		} else {
    			getPreferenceScreen().removePreference( findPreference("app_blacklist_group") );
    		}
    	}
    }

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		if (preference.getKey().equals("rotation_preference")) {
			mPreferences.putBoolean(Index.bool.key.layoutRotationSwitch, mRotationPreference.isChecked(), true);
			
			if (mBlacklistCategory != null) {
				mBlacklistCategory.setEnabled(mRotationPreference.isChecked());
			}
			
		} else if (preference.getKey().equals("application")) {
			String packageName = (String) preference.getSummary();
			Boolean checked = ((CheckBoxPreference) preference).isChecked();
			Boolean exists = mBlacklist.contains(packageName);
			
			if (checked && !exists) {
				mBlacklist.add(packageName);
				
			} else if (!checked && exists) {
				mBlacklist.remove(packageName);
			}
			
			mUpdateBlacklist = true;
		}
		
		return true;
	}
	
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference.getKey().equals("load_apps_preference")) {
			PreferenceCategory category = (PreferenceCategory) findPreference("app_blacklist_group");
			category.removePreference(preference);
			
			loadApplicationList.execute(this.getApplicationContext());
			
			return true;
		}
		
		return false;
	}
	
	protected AsyncTask<Object, Void, List<AppInfo>> loadApplicationList = new AsyncTask<Object, Void, List<AppInfo>>() {
		
		ProgressDialog mProgress;
		
		@Override
		protected void onPreExecute() {
			mProgress = new ProgressDialog(ActivityScreenLayout.this);
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
		
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		@Override
		protected void onPostExecute(List<AppInfo> packages) {
			if (mBlacklistCategory != null) {
				for(int i=0; i < packages.size(); i++) {
					AppInfo appInfo = packages.get(i);
					
					CheckBoxPreference preference = new CheckBoxPreference(ActivityScreenLayout.this);
					preference.setTitle(appInfo.getLabel());
					preference.setSummary(appInfo.getName());
					preference.setKey("application");
					preference.setPersistent(false);
					preference.setChecked( mBlacklist.contains(appInfo.getName()) );
					
					if (appInfo.getIcon() != null) {
						preference.setIcon(appInfo.getIcon());
					}
					
					mBlacklistCategory.addPreference(preference);
				}
			}
			
			if (mProgress != null && mProgress.isShowing()) {
				try {
					mProgress.dismiss();
					
				} catch(Throwable e) {}
			}
		}
	};
}
