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

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.view.View;
import android.widget.CheckBox;

import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Settings;
import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference.OnWidgetBindListener;
import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference.OnWidgetClickListener;
import com.spazedog.xposed.additionsgb.tools.views.WidgetListPreference;

public class ActivityScreenUSB extends PreferenceActivity implements OnPreferenceChangeListener, OnWidgetClickListener, OnWidgetBindListener {
	
	private XServiceManager mPreferences;
	
	private Boolean mSetup = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.activity_screen_usb);
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
    	
    	if (mPreferences != null)
    		mPreferences.commit();
    	
    	mPreferences = null;
    }
    
    private void setup() {
    	if (mSetup != (mSetup = true)) {
	    	String[] wakeplugNames = getResources().getStringArray(R.array.wakeplug_names);
	    	String[] wakeplugValues = getResources().getStringArray(R.array.wakeplug_values);
	    	
	    	WidgetListPreference plugPreference = (WidgetListPreference) findPreference("usb_plug_preference");
	    	plugPreference.setEntries(wakeplugNames);
	    	plugPreference.setEntryValues(wakeplugValues);
	    	plugPreference.setValue( mPreferences.getString(Settings.USB_CONNECTION_PLUG) );
	    	plugPreference.setOnPreferenceChangeListener(this);
	    	plugPreference.setOnWidgetClickListener(this);
	    	plugPreference.setOnWidgetBindListener(this);
	    	plugPreference.loadSummary();
	    	
	    	WidgetListPreference unplugPreference = (WidgetListPreference) findPreference("usb_unplug_preference");
	    	unplugPreference.setEntries(wakeplugNames);
	    	unplugPreference.setEntryValues(wakeplugValues);
	    	unplugPreference.setValue( mPreferences.getString(Settings.USB_CONNECTION_UNPLUG) );
	    	unplugPreference.setOnPreferenceChangeListener(this);
	    	unplugPreference.setOnWidgetClickListener(this);
	    	unplugPreference.setOnWidgetBindListener(this);
	    	unplugPreference.loadSummary();
    	}
    }

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		WidgetListPreference listPreference = (WidgetListPreference) preference;
		listPreference.setValue((String) newValue);
		
		String settingsKey = listPreference.getKey().equals("usb_plug_preference") ? 
				Settings.USB_CONNECTION_PLUG : 
					Settings.USB_CONNECTION_UNPLUG;
		
		mPreferences.putString(settingsKey, (String) newValue, true);
		
		listPreference.loadSummary();
		
		return true;
	}

	@Override
	public void onWidgetClick(Preference preference, View widgetView) {
		String settingsKey = preference.getKey().equals("usb_plug_preference") ? 
				Settings.USB_CONNECTION_SWITCH_PLUG : 
					Settings.USB_CONNECTION_SWITCH_UNPLUG;
		
		CheckBox checbox = (CheckBox) widgetView.findViewById(android.R.id.checkbox);
		checbox.setChecked( !checbox.isChecked() );
		
		((WidgetListPreference) preference).setPreferenceEnabled(checbox.isChecked());
		
		mPreferences.putBoolean(settingsKey, checbox.isChecked(), true);
	}

	@Override
	public void onWidgetBind(Preference preference, View widgetView) {
		String settingsKey = preference.getKey().equals("usb_plug_preference") ? 
				Settings.USB_CONNECTION_SWITCH_PLUG : 
					Settings.USB_CONNECTION_SWITCH_UNPLUG;
		
		CheckBox checbox = (CheckBox) widgetView.findViewById(android.R.id.checkbox);
		checbox.setChecked( mPreferences.getBoolean(settingsKey) );
		
		((WidgetListPreference) preference).setPreferenceEnabled(checbox.isChecked());
	}
}
