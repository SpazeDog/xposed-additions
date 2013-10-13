package com.spazedog.xposed.additionsgb;

import android.content.Context;
import android.preference.Preference;

public final class Common {
	public static final String PACKAGE_NAME = "com.spazedog.xposed.additionsgb";
	public static final String HOOK_PREFERENCES = "hook_settings";
	
	public static final Boolean DEBUG = false;
	
	public static void setDescriptionFromArray(Context context, Preference preference, String arrayName) {
		String[] values = context.getResources().getStringArray(context.getResources().getIdentifier(arrayName + "_values", "array", context.getPackageName()));
		String[] names = context.getResources().getStringArray(context.getResources().getIdentifier(arrayName + "_names", "array", context.getPackageName()));
		
		String value = preference.getSharedPreferences().getString(preference.getKey(), null);
		
		if (value != null) {
			for (int i=0; i < values.length; i++) {
				if (values[i].equals(value)) {
					preference.setSummary(names[i]); break;
				}
			}
		}
	}
}