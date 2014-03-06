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
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable.ConstantState;
import android.os.Build;
import android.text.TextUtils;
import android.view.KeyEvent;

import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;


public final class Common {
	public static final Boolean DEBUG = false;
	private static Boolean ENABLE_DEBUG;
	
	public static final String PACKAGE_NAME = Common.class.getPackage().getName();
	public static final String PACKAGE_NAME_PRO = PACKAGE_NAME + ".pro";
	
	public static final String XSERVICE_NAME = PACKAGE_NAME + ".service.XSERVICE";
	public static final String XSERVICE_PERMISSIONS = PACKAGE_NAME + ".permissions.XSERVICE";
	public static final String XSERVICE_BROADCAST = PACKAGE_NAME + ".filters.XSERVICE";
	
	public static final String PREFERENCE_FILE = "config";
	
	public static List<AppInfo> oPackageListCache;
	private static List<String> oPackageBlackList = new ArrayList<String>(1);
	
	static {
		oPackageBlackList.add("com.android.systemui");
		oPackageBlackList.add("android");
	}
	
	public static final class Index {
		public static final class integer {
			public static final class key {
				public static final String remapTapDelay = "remap_tap_delay";
				public static final String remapPressDelay = "remap_press_delay";
			}
			
			public static final class value {
				public static final Integer remapTapDelay = 100;
				public static final Integer remapPressDelay = 500;
			}
		}
		
		public static final class string {
			public static final class key {
				public static final String usbPlugAction = "usb_plug_action";
				public static final String usbUnPlugAction = "usb_unplug_action";
			}
			
			public static final class value {
				public static final String usbPlugAction = "usb";
				public static final String usbUnPlugAction = "off";
			}
		}
		
		public static final class bool {
			public static final class key {
				public static final String usbPlugSwitch = "usb_plug_switch";
				public static final String usbUnPlugSwitch = "usb_unplug_switch";
				public static final String layoutRotationSwitch = "layout_rotation_switch";
				public static final String enableDebug = "enable_debug";
			}
			
			public static final class value {
				public static final Boolean usbPlugSwitch = false;
				public static final Boolean usbUnPlugSwitch = false;
				public static final Boolean layoutRotationSwitch = false;
				public static final Boolean enableDebug = false;
			}
		}
		
		public static final class array {
			public static final class groupKey {
				public static final String remapKeyConditions = "remap_key_conditions";
				public static final String remapKeyActions_$ = "remap_key_actions:%1$s";
			}
			
			public static final class key {
				public static final String layoutRotationBlacklist = "layout_rotation_blacklist";
				public static final String remapKeys = "remap_keys";
				public static final String forcedHapticKeys = "forced_haptic_keys";
			}
			
			public static final class value {
				public static final ArrayList<String> layoutRotationBlacklist = new ArrayList<String>();
				public static final ArrayList<String> remapKeys = new ArrayList<String>();
				public static final ArrayList<String> forcedHapticKeys = new ArrayList<String>();
			}
		}
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class RemapAction {
		public final Boolean dispatch;
		public final String name;
		public final Integer labelRes;
		public final Integer descriptionRes;
		public final List<String> blacklist = new ArrayList<String>();
		
		public static final List<RemapAction> VALUES = new ArrayList<RemapAction>();
		public static final List<String> NAMES = new ArrayList<String>();
		
		static {
			new RemapAction("disabled", R.string.remap_title_disabled, R.string.remap_summary_disabled, false);
			new RemapAction("guarddismiss", R.string.remap_title_dismissguard, R.string.remap_summary_dismissguard, false, "off", "on");
			new RemapAction("recentapps", R.string.remap_title_recentapps, R.string.remap_summary_recentapps, false, "off");
			new RemapAction("powermenu", R.string.remap_title_powermenu, R.string.remap_summary_powermenu, false, "off");
			new RemapAction("killapp", R.string.remap_title_killapp, R.string.remap_summary_killapp, false, "off", "guard");
			new RemapAction("fliptoggle", R.string.remap_title_fliptoggle, R.string.remap_summary_fliptoggle, false, "off");
			
			if (android.os.Build.VERSION.SDK_INT >= 11) {
				new RemapAction("flipleft", R.string.remap_title_flipleft, R.string.remap_summary_flipleft, false, "off");
				new RemapAction("flipright", R.string.remap_title_flipright, R.string.remap_summary_flipright, false, "off");
			}

			new RemapAction("" + KeyEvent.KEYCODE_POWER, 0, 0, true);
			new RemapAction("" + KeyEvent.KEYCODE_HOME, 0, 0, true, "off", "guard"); 
			new RemapAction("" + KeyEvent.KEYCODE_MENU, 0, 0, true, "off", "guard"); 
			new RemapAction("" + KeyEvent.KEYCODE_BACK, 0, 0, true, "off", "guard");
			new RemapAction("" + KeyEvent.KEYCODE_SEARCH, 0, 0, true, "off");
			new RemapAction("" + KeyEvent.KEYCODE_CAMERA, 0, 0, true, "off"); 
			new RemapAction("" + KeyEvent.KEYCODE_FOCUS, 0, 0, true, "off", "guard");
			new RemapAction("" + KeyEvent.KEYCODE_ENDCALL, 0, 0, true);
			new RemapAction("" + KeyEvent.KEYCODE_MUTE, 0, 0, true); 
			new RemapAction("" + KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0, 0, true); 
			new RemapAction("" + KeyEvent.KEYCODE_MEDIA_NEXT, 0, 0, true);
			new RemapAction("" + KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0, 0, true);
			new RemapAction("" + KeyEvent.KEYCODE_PAGE_UP, 0, 0, true, "off", "guard"); 
			new RemapAction("" + KeyEvent.KEYCODE_PAGE_DOWN, 0, 0, true, "off", "guard"); 
			new RemapAction("" + KeyEvent.KEYCODE_HEADSETHOOK, 0, 0, true);
			new RemapAction("" + KeyEvent.KEYCODE_VOLUME_UP, 0, 0, true); 
			new RemapAction("" + KeyEvent.KEYCODE_VOLUME_DOWN, 0, 0, true); 
			
			if (android.os.Build.VERSION.SDK_INT >= 11) {
				new RemapAction("" + KeyEvent.KEYCODE_VOLUME_MUTE, 0, 0, true);
				new RemapAction("" + KeyEvent.KEYCODE_ZOOM_IN, 0, 0, true, "off", "guard"); 
				new RemapAction("" + KeyEvent.KEYCODE_ZOOM_OUT, 0, 0, true, "off", "guard"); 
			}
		}
		
		private RemapAction(String name, Integer labelRes, Integer descriptionRes, Boolean dispatch, String... blacklist) {
			this.name = name;
			this.labelRes = labelRes;
			this.descriptionRes = descriptionRes;
			this.dispatch = dispatch;
			
			for (int i=0; i < blacklist.length; i++) {
				this.blacklist.add( blacklist[i] );
			}
			
			VALUES.add(this);
			NAMES.add(name);
		}
		
		public String getLabel(Context context) {
			if (labelRes > 0) {
				return context.getResources().getString(labelRes);
			}
			
			return keyToString(name);
		}
		
		public String getDescription(Context context) {
			if (descriptionRes > 0) {
				return context.getResources().getString(descriptionRes);
			}
			
			return null;
		}
	}
	
	public static String actionType(String action) {
		return action == null ? null : 
			action.matches("^[0-9]+$") ? "dispatch" : 
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
			
		} else {
			try {
				return RemapAction.VALUES.get( RemapAction.NAMES.indexOf(action) ).getLabel(context);
				
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
	
	public static List<AppInfo> loadApplicationList(Context context) {
		if (oPackageListCache == null) {
			oPackageListCache = new ArrayList<AppInfo>();
			
			PackageManager packageManager = context.getPackageManager();
			List<PackageInfo> packages = packageManager.getInstalledPackages(0);
			
			for(int i=0; i < packages.size(); i++) {
				PackageInfo packageInfo = packages.get(i);
				
				if (!oPackageBlackList.contains(packageInfo.packageName)) {
					AppInfo appInfo = new AppInfo();
	
					if (android.os.Build.VERSION.SDK_INT >= 11) {
						appInfo.icon = packageInfo.applicationInfo.loadIcon(packageManager).getConstantState();
					}
					
					appInfo.label = packageInfo.applicationInfo.loadLabel(packageManager).toString();
					appInfo.name = packageInfo.packageName;
					
					oPackageListCache.add(appInfo);
				}
			}
		}
		
		return oPackageListCache;
	}
	
	public static class AppInfo {
		public String name;
		public String label;
		public ConstantState icon;
	}
	
	public static Boolean debug() {
		if (ENABLE_DEBUG == null) {
			/*
			 * Avoid recursive calls
			 */
			ENABLE_DEBUG = false;
			
			XServiceManager preferences = XServiceManager.getInstance();
			
			if (preferences != null && preferences.isServiceReady()) {
				ENABLE_DEBUG = preferences.getBoolean(Index.bool.key.enableDebug, Index.bool.value.enableDebug);
				
			} else {
				ENABLE_DEBUG = null;
			}
		}
		
		return DEBUG || (ENABLE_DEBUG != null && ENABLE_DEBUG);
	}
}
