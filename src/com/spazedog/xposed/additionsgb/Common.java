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
import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
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


public final class Common {
	public static final Boolean DEBUG = false;
	private static Boolean ENABLE_DEBUG;
	
	public static final String PACKAGE_NAME = Common.class.getPackage().getName();
	public static final String PACKAGE_NAME_PRO = PACKAGE_NAME + ".pro";
	
	public static final String XSERVICE_NAME = PACKAGE_NAME + ".service.XSERVICE";
	public static final String XSERVICE_PERMISSIONS = PACKAGE_NAME + ".permissions.XSERVICE";
	public static final String XSERVICE_BROADCAST = PACKAGE_NAME + ".filters.XSERVICE";
	
	public static final String PREFERENCE_FILE = "config";
	
	public static final File LOG_FILE = new File(Environment.getDataDirectory(), "data/" + PACKAGE_NAME + "/cache/error.log");
	public static final Long LOG_SIZE = 1024L*512;
	
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
				public static final String remapCallButton = "remap_call_button";
			}
			
			public static final class value {
				public static final Boolean usbPlugSwitch = false;
				public static final Boolean usbUnPlugSwitch = false;
				public static final Boolean layoutRotationSwitch = false;
				public static final Boolean enableDebug = false;
				public static final Boolean remapCallButton = false;
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
		public final Validate validate;
		public final List<String> blacklist = new ArrayList<String>();
		
		public static final List<RemapAction> VALUES = new ArrayList<RemapAction>();
		public static final List<String> NAMES = new ArrayList<String>();
		
		static {
			new RemapAction("disabled", R.string.remap_title_disabled, R.string.remap_summary_disabled, false);
			new RemapAction("guarddismiss", R.string.remap_title_dismissguard, R.string.remap_summary_dismissguard, false, "off", "on");
			new RemapAction("recentapps", R.string.remap_title_recentapps, R.string.remap_summary_recentapps, false, "off");
			new RemapAction("lastapp", R.string.remap_title_last_app, R.string.remap_summary_last_app, false, "off", "guard");
			new RemapAction("powermenu", R.string.remap_title_powermenu, R.string.remap_summary_powermenu, false, "off");
			new RemapAction("killapp", R.string.remap_title_killapp, R.string.remap_summary_killapp, false, "off", "guard");
			new RemapAction("fliptoggle", R.string.remap_title_fliptoggle, R.string.remap_summary_fliptoggle, false, "off");
			
			if (android.os.Build.VERSION.SDK_INT >= 11) {
				new RemapAction("flipleft", R.string.remap_title_flipleft, R.string.remap_summary_flipleft, false, "off");
				new RemapAction("flipright", R.string.remap_title_flipright, R.string.remap_summary_flipright, false, "off");
				new RemapAction("screenshot", R.string.remap_title_screenshot, R.string.remap_summary_screenshot, false, "off");
			}
			
			new RemapAction("torch", R.string.remap_title_torch, R.string.remap_summary_torch, false, new Validate(){ 
				@Override
				public Boolean onValidate(Context context) { 
					return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH); 
				} 
			});

			new RemapAction("" + KeyEvent.KEYCODE_POWER, 0, 0, true);
			new RemapAction("" + KeyEvent.KEYCODE_HOME, 0, 0, true, "off", "guard"); 
			new RemapAction("" + KeyEvent.KEYCODE_MENU, 0, 0, true, "off", "guard"); 
			new RemapAction("" + KeyEvent.KEYCODE_BACK, 0, 0, true, "off", "guard");
			new RemapAction("" + KeyEvent.KEYCODE_SEARCH, 0, 0, true, "off");
			new RemapAction("" + KeyEvent.KEYCODE_CAMERA, 0, 0, true, "off"); 
			new RemapAction("" + KeyEvent.KEYCODE_FOCUS, 0, 0, true, "off", "guard");
			new RemapAction("" + KeyEvent.KEYCODE_CALL, 0, 0, true);
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
		
		private RemapAction(String name, Integer labelRes, Integer descriptionRes, Boolean dispatch, Object... blacklist) {
			int x = blacklist.length-1;
			
			this.name = name;
			this.labelRes = labelRes;
			this.descriptionRes = descriptionRes;
			this.dispatch = dispatch;
			this.validate = blacklist.length > 0 && blacklist[x] instanceof Validate ? (Validate) blacklist[x] : null;
			
			for (int i=0; i < blacklist.length; i++) {
				if (blacklist[i] instanceof String) {
					this.blacklist.add( (String) blacklist[i] );
				}
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
		
		public Boolean isValid(Context context, String condition) {
			return (validate == null || validate.onValidate(context)) && !blacklist.contains(condition);
		}
		
		public static interface Validate {
			public Boolean onValidate(Context context);
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

	public static class AppBuilder implements OnScrollListener {
		
		private WeakReference<ListView> mView;
		
		private LruCache<String, Bitmap> mCache;
		
		private List<AppInfo> mApplications = new ArrayList<AppInfo>();
		
		private Integer mViewCount = 0;
		
		public static interface BuildAppView {
			public void onBuildAppView(ListView view, String name, String label);
		}
		
		public AppBuilder(ListView listView) {
			mView = new WeakReference<ListView>(listView);

			mCache = new LruCache<String, Bitmap>( Math.round(0.25f * Runtime.getRuntime().maxMemory() / 1024) ) {
				@Override
				protected int sizeOf(String key, Bitmap value) {
					return (value.getRowBytes() * value.getHeight()) / 1024;
				}
			};
		}
		
		public void destroy() {
			mCache.evictAll();
			mApplications.clear();
			mView.clear();
		}
		
		private Bitmap drawableToBitmap (Drawable drawable) {
			if (!(drawable instanceof BitmapDrawable)) {
				int width = drawable.getIntrinsicWidth();
				int height = drawable.getIntrinsicHeight();
				
				Bitmap bitmap = Bitmap.createBitmap(width > 0 ? width : 1, height > 0 ? height : 1, Config.ARGB_8888);
				Canvas canvas = new Canvas(bitmap);
				
				drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
				drawable.draw(canvas);
				
				return bitmap;
			}
			
			return ((BitmapDrawable) drawable).getBitmap();
		}

		@Override
		public void onScrollStateChanged(AbsListView view, int scrollState) {}

		@Override
		public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
			/*
			 * All pre-defiened preferences has nothing to do with the application list, so we will skip this.
			 * Because Android removed any view not being displayed, we will always start at index 0. 
			 * But since we might have pre-defined preferences at the top, we do not always want to start there. 
			 * It depends on whether or not we currently are at the top.
			 */
			Integer startI = firstVisibleItem > mViewCount ? 0 : mViewCount-firstVisibleItem;
			Integer startX = firstVisibleItem > mViewCount ? firstVisibleItem-mViewCount : 0;
			
			/*
			 * This will load icons on scroll and keep as many as possible in a cache (amount depends on device RAM).
			 * The adaptor used by Android's preferences automatically removes all views that is currently not being displayed. 
			 * This means that we do not have to worry about removing non-used icons for the GC. 
			 */
			for (int i=startI, x=startX; i < visibleItemCount; i++, x++) {
				if (view.getChildAt(i) != null) {
					AppInfo appInfo = mApplications.get(x);
					String name = appInfo.getName();
					Bitmap bitmap = mCache.get(name);
					ImageView imageView = (ImageView) view.getChildAt(i).findViewById(android.R.id.icon);
					
					/*
					 * TODO: Should be add a background thread for loading icons?
					 */
					if (bitmap == null) {
						bitmap = drawableToBitmap( appInfo.loadIcon( view.getContext() ) );
					}
					
					mCache.put(name, bitmap);
					
					imageView.setImageBitmap(bitmap);
				}
			}
		}
		
		public void build(final BuildAppView callback) {
			View view = mView.get();
			
			if (view != null) {
				(new AsyncTask<Context, Void, List<AppInfo>>() {
					
					ProgressDialog mProgress;
					
					@Override
					protected void onPreExecute() {
						View view = mView.get();
						
						if (view != null) {
							mProgress = new ProgressDialog(view.getContext());
							mProgress.setMessage(view.getContext().getResources().getString(R.string.task_applocation_list));
							mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
							mProgress.setCancelable(false);
							mProgress.setCanceledOnTouchOutside(false);
							mProgress.show();
						}
					}
					
					@Override
					protected List<AppInfo> doInBackground(Context... args) {
						Context context = args[0];
						PackageManager packageManager = context.getPackageManager();
						List<ApplicationInfo> packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
						
						if (mProgress != null) {
							mProgress.setMax(packages.size());
						}
						
						for(int i=0; i < packages.size(); i++) {
							if (mProgress != null) {
								mProgress.setProgress( (i+1) );
							}
							
							ApplicationInfo app = packages.get(i);
							String label = (String) packageManager.getApplicationLabel(app);
							
							if (label != null && !label.equals(app.packageName)) {
								mApplications.add(new AppInfo(label, app));
							}
						}
						
						Collections.sort(mApplications);
						
						return mApplications;
					}
					
					@Override
					protected void onPostExecute(List<AppInfo> packages) {
						ListView view = mView.get();
						
						if (view != null && mProgress != null && mProgress.isShowing()) {
							mViewCount = view.getCount();
							
							for(int i=0; i < packages.size(); i++) {
								AppInfo appInfo = packages.get(i);
								
								callback.onBuildAppView(view, appInfo.getName(), appInfo.getLabel());
							}

							try {
								mProgress.dismiss();
								
							} catch(Throwable e) {}
							
							/*
							 * We don't display images in GB, so no need to track
							 * scrolling. 
							 */
							if (android.os.Build.VERSION.SDK_INT >= 11) {
								view.setOnScrollListener(AppBuilder.this);
							}
						}
					}
					
				}).execute( view.getContext().getApplicationContext() );
			}
		}
		
		public static class AppInfo implements Comparable<AppInfo> {
			
			private ApplicationInfo mApplicationInfo;
			
			private String mLabel;
			
			private AppInfo(String label, ApplicationInfo app) {
				mApplicationInfo = app;
				mLabel = label;
			}
			
			@Override
			public int compareTo(AppInfo comparible) {
				return Collator.getInstance(Locale.getDefault()).
						compare(mLabel, comparible.mLabel);
			}
			
			public Drawable loadIcon(Context context) {
				return context.getPackageManager().getApplicationIcon(mApplicationInfo);
			}
			
			public String getName() {
				return mApplicationInfo.packageName;
			}
			
			public String getLabel() {
				return mLabel;
			}
		}
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
