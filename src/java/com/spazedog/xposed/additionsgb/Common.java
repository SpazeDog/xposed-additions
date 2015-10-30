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

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.util.LruCache;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ImageView;
import android.widget.ListView;

import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Actions;
import com.spazedog.xposed.additionsgb.configs.Settings;


public final class Common {
	
	public static final String TORCH_INTENT_ACTION = PACKAGE_NAME + ".TOGGLE_FLASHLIGHT";
	
	public static final String PREFERENCE_FILE = "config";
	
	public static String actionType(String action) {
		return action == null ? null : 
			action.matches("^[0-9]+$") ? "dispatch" : 
				action.startsWith("tasker:") ? "tasker" : 
				action.startsWith("shortcut:") ? "shortcut" : 
				action.contains(".") ? "launcher" : "custom";
	}
	
	public static String actionToString(Context context, String action) {
		String type = actionType(action);
		
		if ("dispatch".equals(type)) {
			return keyToString( Integer.parseInt(action) );
			
		} else if ("launcher".equals(type)) {
			try {
				PackageManager packageManager = context.getPackageManager();
				ApplicationInfo applicationInfo = packageManager.getApplicationInfo(action, 0);
				
				return (String) packageManager.getApplicationLabel(applicationInfo);
				
			} catch(Throwable e) {}
			
		} else if ("tasker".equals(type)) {
			try {
				return action.replace("tasker:", "Tasker: ");
				
			} catch (Throwable e) {}
			
		} else if ("shortcut".equals(type)) {
			try {
				return action.substring(0, action.indexOf(":", "shortcut".length()+1)).replace("shortcut:", "Shortcut: ");
				
			} catch (Throwable e) {}
			
		}  else {
			try {
				for (RemapAction current : Actions.COLLECTION) {
					if (current.getAction().equals(action)) {
						return current.getLabel(context);
					}
				}
				
			} catch(Throwable e) {}
		}
		
		return null;
	}
	
	public static String conditionToString(Context context, String condition) {
		Integer id = context.getResources().getIdentifier("condition_type_$" + condition, "string", PACKAGE_NAME);
		
		if (id > 0) {
			return context.getResources().getString(id);
			
		} else {
			try {
				PackageManager packageManager = context.getPackageManager();
				ApplicationInfo applicationInfo = packageManager.getApplicationInfo(condition, 0);
				
				return (String) packageManager.getApplicationLabel(applicationInfo);
				
			} catch(Throwable e) {}
		}
		
		return condition;
	}
	
	public static String keyToString(String keyCode) {
		String[] codes = keyCode.trim().split("[^0-9]+");
		List<String> output = new ArrayList<String>();
		
		for (int i=0; i < codes.length; i++) {
			if(codes[i] != null && !codes[i].equals("0")) {
				output.add(keyToString( Integer.parseInt(codes[i]) ));
			}
		}
		
		return TextUtils.join("+", output);
	}

	@SuppressLint("NewApi")
	public static String keyToString(Integer keyCode) {
		/*
		 * KeyEvent to string is not supported in Gingerbread, 
		 * so we define the most basics ourself.
		 */
		
		if (keyCode != null) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_VOLUME_UP: return "Volume Up";
				case KeyEvent.KEYCODE_VOLUME_DOWN: return "Volume Down";
				case KeyEvent.KEYCODE_SETTINGS: return "Settings";
				case KeyEvent.KEYCODE_SEARCH: return "Search";
				case KeyEvent.KEYCODE_POWER: return "Power";
				case KeyEvent.KEYCODE_NOTIFICATION: return "Notification";
				case KeyEvent.KEYCODE_MUTE: return "Mic Mute";
				case KeyEvent.KEYCODE_MUSIC: return "Music";
				case KeyEvent.KEYCODE_MOVE_HOME: return "Home";
				case KeyEvent.KEYCODE_MENU: return "Menu";
				case KeyEvent.KEYCODE_MEDIA_STOP: return "Media Stop";
				case KeyEvent.KEYCODE_MEDIA_REWIND: return "Media Rewind";
				case KeyEvent.KEYCODE_MEDIA_RECORD: return "Media Record";
				case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return "Media Previous";
				case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return "Media Play/Pause";
				case KeyEvent.KEYCODE_MEDIA_PLAY: return "Media Play";
				case KeyEvent.KEYCODE_MEDIA_PAUSE: return "Media Pause";
				case KeyEvent.KEYCODE_MEDIA_NEXT: return "Media Next";
				case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return "Media Fast Forward";
				case KeyEvent.KEYCODE_HOME: return "Home";
				case KeyEvent.KEYCODE_FUNCTION: return "Function";
				case KeyEvent.KEYCODE_FOCUS: return "Camera Focus";
				case KeyEvent.KEYCODE_ENDCALL: return "End Call";
				case KeyEvent.KEYCODE_DPAD_UP: return "DPad Up";
				case KeyEvent.KEYCODE_DPAD_RIGHT: return "DPad Right";
				case KeyEvent.KEYCODE_DPAD_LEFT: return "DPad Left";
				case KeyEvent.KEYCODE_DPAD_DOWN: return "DPad Down";
				case KeyEvent.KEYCODE_DPAD_CENTER: return "DPad Center";
				case KeyEvent.KEYCODE_CAMERA: return "Camera";
				case KeyEvent.KEYCODE_CALL: return "Call";
				case KeyEvent.KEYCODE_BUTTON_START: return "Start";
				case KeyEvent.KEYCODE_BUTTON_SELECT: return "Select";
				case KeyEvent.KEYCODE_BACK: return "Back";
				case KeyEvent.KEYCODE_APP_SWITCH: return "App Switch";
				case KeyEvent.KEYCODE_3D_MODE: return "3D Mode";
				case KeyEvent.KEYCODE_ASSIST: return "Assist";
				case KeyEvent.KEYCODE_PAGE_UP: return "Page Up";
				case KeyEvent.KEYCODE_PAGE_DOWN: return "Page Down";
				case KeyEvent.KEYCODE_HEADSETHOOK: return "Headset Hook";
			}
			
			if (android.os.Build.VERSION.SDK_INT >= 11) {
				switch (keyCode) {
					case KeyEvent.KEYCODE_VOLUME_MUTE: return "Volume Mute";
					case KeyEvent.KEYCODE_ZOOM_OUT: return "Zoom Out";
					case KeyEvent.KEYCODE_ZOOM_IN: return "Zoom In";
				}
			}
			
			if (android.os.Build.VERSION.SDK_INT >= 12) {
				String codeName = KeyEvent.keyCodeToString(keyCode);
				
				if (codeName.startsWith("KEYCODE_")) {
					String[] codeWords = codeName.toLowerCase(Locale.US).split("_");
					StringBuilder builder = new StringBuilder();
					
					for (int i=1; i < codeWords.length; i++) {
						char[] codeChars = codeWords[i].trim().toCharArray();
						
						codeChars[0] = Character.toUpperCase(codeChars[0]);
						
						if (i > 1) {
							builder.append(" ");
						}

						builder.append(codeChars);
					}
					
					return builder.toString();
				}
			}
		}
		
		return "" + keyCode;
	}
	
	public static Integer getConditionIdentifier(Context context, String condition) {
		return context.getResources().getIdentifier("condition_type_$" + condition, "string", Common.PACKAGE_NAME);
	}
	
	/*
	 * Quantity Strings are broken on some Platforms and phones which is described in the below tracker. 
	 * To make up for this, we use this little helper. We don't need options like 'few' or 'many', so
	 * no larger library replacement is needed. 
	 * 
	 * http://code.google.com/p/android/issues/detail?id=8287
	 */
	public static int getQuantityResource(Resources resources, String idRef, int quantity) {
		int id = resources.getIdentifier(idRef + "_$" + quantity, "string", PACKAGE_NAME);
		
		if (id == 0) {
			id = resources.getIdentifier(idRef, "string", PACKAGE_NAME);
		}
		
		return id;
	}
	
	public static Boolean debug() {
		if (ENABLE_DEBUG == null) {
			/*
			 * Avoid recursive calls
			 */
			ENABLE_DEBUG = false;
			
			XServiceManager preferences = XServiceManager.getInstance();
			
			if (preferences != null && preferences.isServiceReady()) {
				ENABLE_DEBUG = preferences.getBoolean(Settings.DEBUG_ENABLE_LOGGING);
				
			} else {
				ENABLE_DEBUG = null;
			}
		}
		
		return DEBUG || ENABLE_DEBUG == null || ENABLE_DEBUG;
	}

	public static class PlaceHolder {
		private final String mKey;
		
		public PlaceHolder(String key) {
			mKey = key;
		}
		
		public String get(Object... replacements) {
			return String.format(mKey, replacements);
		}
	}
	
	public static class RemapAction {
		private final Boolean mDispatchAction;
		private final String mAction;
		private final Integer mMinSDK;
		private final Integer mLabelRes;
		private final Integer mDescRes;
		private final Integer mAlertRes;
		private final Integer mNoticeRes;
		
		private final List<String> mConditionBlacklist = new ArrayList<String>();
		
		private final Validate mValidator;
		
		public RemapAction(String name, Integer sdk, Integer labelRes, Integer descriptionRes, Integer alertRes, Integer noticeRes, Object... blacklist) {
			mDispatchAction = name.matches("^[0-9]+$");
			mAction = name;
			mMinSDK = sdk;
			mLabelRes = labelRes;
			mDescRes = descriptionRes;
			mAlertRes = alertRes;
			mNoticeRes = noticeRes;
			mValidator = blacklist.length > 0 && blacklist[ blacklist.length-1 ] instanceof Validate ? (Validate) blacklist[ blacklist.length-1 ] : null;
			
			for (int i=0; i < blacklist.length; i++) {
				if (blacklist[i] instanceof String) {
					mConditionBlacklist.add( (String) blacklist[i] );
				}
			}
		}
		
		public RemapAction(Integer key, Integer sdk, Integer labelRes, Integer descriptionRes, Integer alertRes, Integer noticeRes, Object... blacklist) {
			this(""+key, sdk, labelRes, descriptionRes, alertRes, noticeRes, blacklist);
		}
		
		public String getAction() {
			return mAction;
		}
		
		public String getLabel(Context context) {
			if (mLabelRes > 0) {
				return context.getResources().getString(mLabelRes);
			}
			
			return keyToString(mAction);
		}
		
		public String getDescription(Context context) {
			if (mDescRes > 0) {
				return context.getResources().getString(mDescRes);
			}
			
			return null;
		}
		
		public String getNotice(Context context) {
			if (mNoticeRes > 0) {
				return context.getResources().getString(mNoticeRes);
			}
			
			return null;
		}
		
		public String getAlert(Context context) {
			if (mAlertRes > 0) {
				return context.getResources().getString(mAlertRes);
			}
			
			return null;
		}
		
		public Boolean isDispatchAction() {
			return mDispatchAction;
		}
		
		public Boolean hasNotice(Context context) {
			return mNoticeRes > 0 && (mValidator == null || mValidator.onDisplayNotice(context));
		}
		
		public Boolean hasAlert(Context context) {
			return mAlertRes > 0 && (mValidator == null || mValidator.onDisplayAlert(context));
		}
		
		public Boolean isValid(Context context, String condition) {
			return android.os.Build.VERSION.SDK_INT >= mMinSDK && !mConditionBlacklist.contains(condition) && (mValidator == null || mValidator.onValidate(context));
		}
		
		public static abstract class Validate {
			public Boolean onValidate(Context context) { return true; };
			public Boolean onDisplayAlert(Context context) { return false; }
			public Boolean onDisplayNotice(Context context) { return true; }
		}
	}
}
