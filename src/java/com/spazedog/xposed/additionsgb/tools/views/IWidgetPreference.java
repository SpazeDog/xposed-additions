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

package com.spazedog.xposed.additionsgb.tools.views;

import android.preference.Preference;
import android.view.View;

public interface IWidgetPreference {
	public static interface OnWidgetClickListener {
		public void onWidgetClick(Preference preference, View widgetView);
	}
	
	public static interface OnWidgetBindListener {
		public void onWidgetBind(Preference preference, View widgetView);
	}
	
	public void setWidgetEnabled(boolean enabled);
	public void setPreferenceEnabled(boolean enabled);
	
	public void setOnWidgetClickListener(OnWidgetClickListener listener);
	public void setOnWidgetBindListener(OnWidgetBindListener listener);
	
	public void setTag(Object tag);
	public Object getTag();
}
