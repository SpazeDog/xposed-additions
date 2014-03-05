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

import android.content.Context;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;

import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference.OnWidgetBindListener;
import com.spazedog.xposed.additionsgb.tools.views.IWidgetPreference.OnWidgetClickListener;

public class WidgetListPreference extends ListPreference implements IWidgetPreference {
	
	protected OnWidgetClickListener mOnWidgetClickListener;
	protected OnWidgetBindListener mOnWidgetBindListener;
	
	protected View mWidgetView;
	protected View mTitleView;
	protected View mSummaryView;
	protected View mIconView;
	
	protected Boolean mWidgetEnabled = true;
	protected Boolean mPreferenceEnabled = true;
	
	protected Boolean mBinded = false;
	
	protected Object mTag;
	
	OnPreferenceClickListener listener;
	
	public WidgetListPreference(Context context) {
		super(context);
	}

	public WidgetListPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	@Override
	protected void onBindView(View view) {
		super.onBindView(view);

		mTitleView = view.findViewById(android.R.id.title);
		mSummaryView = view.findViewById(android.R.id.summary);
		mIconView = view.findViewById(android.R.id.icon);
				
		mWidgetView = view.findViewById(android.R.id.widget_frame);
		mWidgetView.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View view) {
				if (mOnWidgetClickListener != null && mWidgetEnabled) {
					mOnWidgetClickListener.onWidgetClick(WidgetListPreference.this, view);
				}
			}
		});
		
		super.setOnPreferenceClickListener(new OnPreferenceClickListener(){
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if (mPreferenceEnabled && listener != null) {
					return listener.onPreferenceClick(preference);
				}
				
				return mPreferenceEnabled == false;
			}
		});
		
		if (!mBinded) {
			mBinded = true;
		}
			
		setWidgetEnabled(mWidgetEnabled);
		setPreferenceEnabled(mPreferenceEnabled);
		
		if (mOnWidgetBindListener != null) {
			mOnWidgetBindListener.onWidgetBind(this, mWidgetView);
		}
	}
	
	@Override
	public void setWidgetEnabled(boolean enabled) {
		if (isEnabled()) {
			mWidgetEnabled = enabled;
			
			if (mBinded) {
				mWidgetView.setEnabled(enabled);
			}
		}
	}
	
	@Override
	public void setPreferenceEnabled(boolean enabled) {
		if (isEnabled()) {
			mPreferenceEnabled = enabled;
			
			if (mBinded) {
				mTitleView.setEnabled(enabled);
				mSummaryView.setEnabled(enabled);
				
				if (android.os.Build.VERSION.SDK_INT >= 11) {
					mIconView.setEnabled(enabled);
				}
			}
		}
	}
	
	@Override
	public void setEnabled(boolean enabled) {
		mWidgetEnabled = enabled;
		mPreferenceEnabled = enabled;
		
		super.setEnabled(enabled);
	}
	
	@Override
	protected void onClick() {
		if (mPreferenceEnabled) {
			super.onClick();
		}
	}
	
	@Override
	public void setOnPreferenceClickListener(OnPreferenceClickListener onPreferenceClickListener) {
		listener = onPreferenceClickListener;
	}
	
	@Override
	public void setOnWidgetClickListener(OnWidgetClickListener listener) {
		mOnWidgetClickListener = listener;
	}
	
	@Override
	public void setOnWidgetBindListener(OnWidgetBindListener listener) {
		mOnWidgetBindListener = listener;
	}
	
	public void loadSummary() {
		String curValue = getValue();
		CharSequence[] entryValues = getEntryValues();
		CharSequence[] entrySummaries = getEntries();
		
		if (curValue != null) {
			for (int i=0; i < entryValues.length; i++) {
				if (entryValues[i].equals(curValue)) {
					setSummary(entrySummaries[i]); break;
				}
			}
		}
	}
	
	@Override
	public void setTag(Object tag) {
		mTag = tag;
	}
	
	@Override
	public Object getTag() {
		return mTag;
	}
}
