package com.spazedog.xposed.additions;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import android.content.Context;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class SettingsInjector implements IXposedHookLoadPackage{
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
	    			checkBoxPreferencePower.setKey("powerDisable");
	    			checkBoxPreferencePower.setTitle("Disable power button");
	    			checkBoxPreferencePower.setSummary("Do not turn on the screen when the power button is pressed");
	    			checkBoxPreferencePower.setOnPreferenceClickListener((OnPreferenceClickListener) param.thisObject);
	    			checkBoxPreferencePower.setChecked( Settings.System.getInt(context.getContentResolver(), "disable_power_button", 0) == 1 );
	    			
	    			CheckBoxPreference checkBoxPreferenceRotation = new CheckBoxPreference(context);
	    			checkBoxPreferenceRotation.setKey("lockscreenRotation");
	    			checkBoxPreferenceRotation.setTitle("Include lockscreen rotation");
	    			checkBoxPreferenceRotation.setSummary("Include display rotation settings in the lock screen");
	    			checkBoxPreferenceRotation.setOnPreferenceClickListener((OnPreferenceClickListener) param.thisObject);
	    			checkBoxPreferenceRotation.setChecked( Settings.System.getInt(context.getContentResolver(), "enable_lockscreen_rotation", 0) == 1 );
	    			
	    			CheckBoxPreference checkBoxPreferencePlugged = new CheckBoxPreference(context);
	    			checkBoxPreferencePlugged.setKey("usbChange");
	    			checkBoxPreferencePlugged.setTitle("Wake on Plugged/Unplugged");
	    			checkBoxPreferencePlugged.setSummary("Wake the device when the USB is plugged or unplugged");
	    			checkBoxPreferencePlugged.setOnPreferenceClickListener((OnPreferenceClickListener) param.thisObject);
	    			checkBoxPreferencePlugged.setChecked( Settings.System.getInt(context.getContentResolver(), "wake_on_usb_change", 0) == 1 );
	    			
	    			screen.addPreference(preferenceCategory);
	    			screen.addPreference(checkBoxPreferencePower);
	    			screen.addPreference(checkBoxPreferenceRotation);
	    			screen.addPreference(checkBoxPreferencePlugged);
	    		}
    		});
    		
    		findAndHookMethod("com.android.settings.DisplaySettings", lpparam.classLoader, "onPreferenceTreeClick", PreferenceScreen.class, Preference.class, new XC_MethodHook() {
	    		@Override
	    		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
	    			if (((Preference) param.args[1]).getKey().equals("powerDisable")) {
	    				Settings.System.putInt(((PreferenceFragment) param.thisObject).getActivity().getContentResolver(), "disable_power_button", ((CheckBoxPreference) param.args[1]).isChecked() ? 1 : 0);
	    				
	    				param.setResult(true);
	    				
	    			} else if (((Preference) param.args[1]).getKey().equals("lockscreenRotation")) {
	    				Settings.System.putInt(((PreferenceFragment) param.thisObject).getActivity().getContentResolver(), "enable_lockscreen_rotation", ((CheckBoxPreference) param.args[1]).isChecked() ? 1 : 0);
	    				
	    				param.setResult(true);
	    				
	    			} else if (((Preference) param.args[1]).getKey().equals("usbChange")) {
	    				Settings.System.putInt(((PreferenceFragment) param.thisObject).getActivity().getContentResolver(), "wake_on_usb_change", ((CheckBoxPreference) param.args[1]).isChecked() ? 1 : 0);
	    				
	    				param.setResult(true);
	    			}
	    		}
    		});
    	}
	}
}
