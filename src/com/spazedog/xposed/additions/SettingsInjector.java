package com.spazedog.xposed.additions;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import android.content.Context;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class SettingsInjector implements IXposedHookLoadPackage, OnPreferenceChangeListener, OnPreferenceClickListener {
	public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
    	if (lpparam.packageName.equals("com.android.settings")) {
    		findAndHookMethod("com.android.settings.DisplaySettings", lpparam.classLoader, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
	    		@Override
	    		protected void afterHookedMethod(MethodHookParam param) throws Throwable {
	    			PreferenceScreen screen = ((PreferenceFragment) param.thisObject).getPreferenceScreen();
	    			Context context = ((PreferenceFragment) param.thisObject).getActivity();
	    			
	    			PreferenceCategory preferenceCategory = new PreferenceCategory(context);
	    			preferenceCategory.setTitle("Xposed Additions");
	    			
	    			CheckBoxPreference checkBoxPreferencePower = new CheckBoxPreference(context);
	    			checkBoxPreferencePower.setKey("xposed_pref_disable_power_button");
	    			checkBoxPreferencePower.setTitle("Disable power button");
	    			checkBoxPreferencePower.setSummary("Do not turn on the screen when the power button is pressed");
	    			checkBoxPreferencePower.setOnPreferenceClickListener(SettingsInjector.this);
	    			checkBoxPreferencePower.setChecked( Settings.System.getInt(context.getContentResolver(), "xposed_disable_power_button", 0) == 1 );
	    			
	    			CheckBoxPreference checkBoxPreferenceRotation = new CheckBoxPreference(context);
	    			checkBoxPreferenceRotation.setKey("xposed_pref_enable_lockscreen_rotation");
	    			checkBoxPreferenceRotation.setTitle("Include lockscreen rotation");
	    			checkBoxPreferenceRotation.setSummary("Include display rotation settings in the lock screen");
	    			checkBoxPreferenceRotation.setOnPreferenceClickListener(SettingsInjector.this);
	    			checkBoxPreferenceRotation.setChecked( Settings.System.getInt(context.getContentResolver(), "xposed_enable_lockscreen_rotation", 0) == 1 );
	    			
	    			ListPreference listPreferencePlugged = new ListPreference(context);
	    			listPreferencePlugged.setKey("xposed_pref_wakeon_usb_change");
	    			listPreferencePlugged.setEntryValues(new String[]{"", "pluggedin|pluggedout", "pluggedin", "pluggedout"});
	    			listPreferencePlugged.setEntries(new String[]{"Never", "Always", "Plugged", "Unplugged"});
	    			listPreferencePlugged.setTitle("Wake on Plugged/Unplugged");
	    			listPreferencePlugged.setSummary("Wake the device when the USB is plugged or unplugged");
	    			listPreferencePlugged.setOnPreferenceChangeListener(SettingsInjector.this);
	    			
	    			screen.addPreference(preferenceCategory);
	    			screen.addPreference(checkBoxPreferencePower);
	    			screen.addPreference(checkBoxPreferenceRotation);
	    			screen.addPreference(listPreferencePlugged);
	    		}
    		});
    	}
	}
	
	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		Settings.System.putString(preference.getContext().getContentResolver(), "xposed_wakeon_usb_change", (String) newValue);

		return true;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference.getKey().equals("xposed_pref_disable_power_button")) {
			Settings.System.putInt(preference.getContext().getContentResolver(), "xposed_disable_power_button", ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
			
			return true;
			
		} else if (preference.getKey().equals("xposed_pref_enable_lockscreen_rotation")) {
			Settings.System.putInt(preference.getContext().getContentResolver(), "xposed_enable_lockscreen_rotation", ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
			
			return true;
		}
		
		return false;
	}
}
