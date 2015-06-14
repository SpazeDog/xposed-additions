package com.spazedog.xposed.additionsgb;

import com.spazedog.xposed.additionsgb.utils.SettingsHelper;

interface IServicePreferences {
	void writeSettingsData(in SettingsHelper.SettingsData data);
	SettingsHelper.SettingsData readSettingsData();
}