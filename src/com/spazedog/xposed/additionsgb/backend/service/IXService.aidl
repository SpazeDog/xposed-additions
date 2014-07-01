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

package com.spazedog.xposed.additionsgb.backend.service;

import com.spazedog.xposed.additionsgb.backend.service.IXServiceChangeListener;

/** {@hide} */
interface IXService {
	void putStringArray(String key, in List<String> value, int preserve);
	void putString(String key, String value, int preserve);
	void putInt(String key, int value, int preserve);
	void putBoolean(String key, boolean value, int preserve);
	
	List<String> getStringArray(String key, in List<String> defaultValue);
	String getString(String key, String defaultValue);
	int getInt(String key, int defaultValue);
	boolean getBoolean(String key, boolean defaultValue);
	
	boolean remove(String key);
	
	String getType(String key);
	
	List<String> getKeys();
	List<String> getPreservedKeys();
	
	boolean apply();
	void commit();
	
	boolean isUnlocked();
	
	boolean isReady();
	
	int getVersion();
	
	void setOnChangeListener(IXServiceChangeListener listener);
	
	void sendBroadcast(String action, in Bundle data);
}