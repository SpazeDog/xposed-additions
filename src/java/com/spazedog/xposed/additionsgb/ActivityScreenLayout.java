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

import android.annotation.TargetApi;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.spazedog.xposed.additionsgb.Common.AppBuilder;
import com.spazedog.xposed.additionsgb.Common.AppBuilder.BuildAppView;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Settings;

public class ActivityScreenLayout extends PreferenceActivity implements OnPreferenceClickListener {
	
	private XServiceManager mPreferences;
	
	private Boolean mSetup = false;
	
	private PreferenceCategory mBlacklistCategory;
	private CheckBoxPreference mRotationPreference;
	
	private ArrayList<String> mBlacklist = new ArrayList<String>();
	
	private Boolean mUpdateBlacklist = false;
	
	private AppBuilder mAppBuilder;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.activity_screen_layout);
		
		mAppBuilder = new AppBuilder( getListView() );
	}
	
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		
		if (Build.VERSION.SDK_INT >= 14) {
			LinearLayout root = (LinearLayout)findViewById(android.R.id.list).getParent().getParent().getParent();
			Toolbar bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.actionbar_v14_layout, root, false);
			bar.setTitle(R.string.category_title_layout);
			
			root.addView(bar, 0);
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
    protected void onPause() {
    	super.onPause();
    	
    	if (mUpdateBlacklist && mPreferences != null) {
    		mPreferences.putStringArray(Settings.LAYOUT_GLOBAL_ROTATION_BLACKLIST, mBlacklist, true);
        	mPreferences.apply();
    	}
    }
    
    @Override
    protected void onStop() {
    	super.onStop();

    	mPreferences = null;
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	
    	mAppBuilder.destroy();
    }
    
    private void setup() {
    	if (mSetup != (mSetup = true)) {
    		mRotationPreference = (CheckBoxPreference) findPreference("rotation_preference");
    		mRotationPreference.setChecked( mPreferences.getBoolean(Settings.LAYOUT_ENABLE_GLOBAL_ROTATION) );
    		
    		if (mPreferences.isPackageUnlocked()) {
    			mBlacklistCategory = (PreferenceCategory) findPreference("app_blacklist_group");
    			mBlacklistCategory.setEnabled( mRotationPreference.isChecked() );
    			
    			mBlacklist = (ArrayList<String>) mPreferences.getStringArray(Settings.LAYOUT_GLOBAL_ROTATION_BLACKLIST, new ArrayList<String>());
    			
    			findPreference("load_apps_preference").setOnPreferenceClickListener(this);
    			
    		} else {
    			getPreferenceScreen().removePreference( findPreference("app_blacklist_group") );
    		}
    	}
    }

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		if (preference.getKey().equals("rotation_preference")) {
			mPreferences.putBoolean(Settings.LAYOUT_ENABLE_GLOBAL_ROTATION, mRotationPreference.isChecked(), true);
			
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
			mBlacklistCategory.removePreference(preference);

			Drawable tmpIcon = new ColorDrawable(android.R.color.transparent);
			
			try {
				tmpIcon = getPackageManager().getApplicationIcon("android");
				
			} catch (NameNotFoundException e) {}
			
			final Drawable defaultIcon = tmpIcon;
			
			mAppBuilder.build(new BuildAppView(){
				@TargetApi(Build.VERSION_CODES.HONEYCOMB)
				@Override
				public void onBuildAppView(ListView view, String name, String label) {
					CheckBoxPreference preference = new CheckBoxPreference(ActivityScreenLayout.this);
					preference.setTitle(label);
					preference.setSummary(name);
					preference.setKey("application");
					preference.setPersistent(false);
					preference.setChecked( mBlacklist.contains(name) );
					
					/*
					 * Add a template icon to create the correct
					 * default configurations for the image view
					 */
					if (android.os.Build.VERSION.SDK_INT >= 11) {
						preference.setIcon(defaultIcon);
					}
					
					mBlacklistCategory.addPreference(preference);
				}
			});
			
			return true;
		}
		
		return false;
	}
}
