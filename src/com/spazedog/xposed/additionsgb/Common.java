package com.spazedog.xposed.additionsgb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.Vibrator;
import android.preference.ListPreference;
import android.provider.Settings;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.widget.Toast;
import de.robv.android.xposed.XSharedPreferences;

public final class Common {
	
	public static final String PACKAGE_NAME = Common.class.getPackage().getName();
	public static final String PACKAGE_NAME_PRO = PACKAGE_NAME+".pro";
	public static final String HOOK_PREFERENCES = "hook_settings";
	
	public static final Boolean DEBUG = false;
	
	public static SharedPreferences mPreferences;
	
	public static final class Remap {
		public static final String KEY_OFF_ENABLED = "keycode_enabled:off/";
		public static final String KEY_OFF_ACTION_CLICK = "keycode_action_click:off/";
		public static final String KEY_OFF_ACTION_TAP = "keycode_action_tap:off/";
		public static final String KEY_OFF_ACTION_PRESS = "keycode_action_press:off/";
		
		public static final String KEY_ON_ENABLED = "keycode_enabled:on/";
		public static final String KEY_ON_ACTION_CLICK = "keycode_action_click:on/";
		public static final String KEY_ON_ACTION_TAP = "keycode_action_tap:on/";
		public static final String KEY_ON_ACTION_PRESS = "keycode_action_press:on/";
		
		public static final String KEY_COLLECTION = "key_collection";
		public static final String KEY_COLLECTION_SECONDARY = "key_collection/";
		
		public static final String KEY_DELAY_PRESS = "key_delay_press";
		public static final String KEY_DELAY_TAP = "key_delay_tap";
		
		public static Boolean isKeyEnabled(Integer code, Boolean screenIsOff) {
			return mPreferences.getBoolean((screenIsOff ? KEY_OFF_ENABLED : KEY_ON_ENABLED) + code, false);
		}
		
		public static String getKeyClick(Integer code, Boolean screenIsOff) {
			return mPreferences.getString((screenIsOff ? KEY_OFF_ACTION_CLICK : KEY_ON_ACTION_CLICK) + code, "default");
		}
		
		public static String getKeyTap(Integer code, Boolean screenIsOff) {
			return mPreferences.getString((screenIsOff ? KEY_OFF_ACTION_TAP : KEY_ON_ACTION_TAP) + code, "disabled");
		}
		
		public static String getKeyPress(Integer code, Boolean screenIsOff) {
			return mPreferences.getString((screenIsOff ? KEY_OFF_ACTION_PRESS : KEY_ON_ACTION_PRESS) + code, "default");
		}
		
		public static Integer getPressDelay() {
			return Integer.parseInt( mPreferences.getString(KEY_DELAY_PRESS, "500") );
		}
		
		public static Integer getTapDelay() {
			return Integer.parseInt( mPreferences.getString(KEY_DELAY_TAP, "150") );
		}
		
		public static Integer[] getKeyList() {
			return getKeyList(0);
		}
		
		public static Integer[] getKeyList(Integer keyCode) {
			/*
			 * Gingerbread does not support StringSet in SharedPreferences
			 */
			String[] keyArray = mPreferences.getString(keyCode == 0 ? KEY_COLLECTION : KEY_COLLECTION_SECONDARY+keyCode, "").split(",");
			List<Integer> keyList = new ArrayList<Integer>();
			
			for (int i=0; i < keyArray.length; i++) {
				if (keyArray[i] != null && keyArray[i].matches("^[0-9]+$")) {
					keyList.add(Integer.parseInt(keyArray[i]));
				}
			}
			
			return keyList.toArray(new Integer[ keyList.size() ]);
		}
	}
	
	public static final class USBPlug {
		public static final String KEY_ACTION_PLUG = "usb_action_plug";
		public static final String KEY_ACTION_UNPLUG = "usb_action_unplug";
		
		public static String getStateAction(Boolean plugged) {
			return mPreferences.getString(plugged ? KEY_ACTION_PLUG : KEY_ACTION_UNPLUG, "default");
		}
		
		public static Boolean isStateEnabled(Boolean plugged) {
			String key = plugged ? KEY_ACTION_PLUG : KEY_ACTION_UNPLUG;
			
			if (mPreferences.contains(key) && !mPreferences.getString(key, "default").equals("default")) {
				return true;
			}
			
			return false;
		}
	}
	
	public static final class AppLayout {
		public static final String KEY_APP_GLOBAL_ORIENTATION = "app_global_orientation";
		
		public static Boolean isGlobalOrientationEnabled() {
			return mPreferences.getBoolean(KEY_APP_GLOBAL_ORIENTATION, false);
		}
	}
	
	public static final class BroadcastOptions {
		public static final String INTENT_ACTION_REQUEST = PACKAGE_NAME + ".intent.action.REQUEST";
		public static final String INTENT_ACTION_RESPONSE = PACKAGE_NAME + ".intent.action.RESPONSE";
		
		public static final String PERMISSION_REQUEST = PACKAGE_NAME + ".permission.REQUEST";

		public static final String REQUEST_ENABLE_KEYCODE_INTERCEPT = "enable_keycode_intercept";
		public static final String REQUEST_DISABLE_KEYCODE_INTERCEPT = "disable_keycode_intercept";
		public static final String REQUEST_RELOAD_CONFIGS = "reload_configs";
		public static final String REQUEST_HANDSHAKE = "handshake";
	}
	
	public static void loadSharedPreferences(Context context) {
		loadSharedPreferences(context, false);
	}
	
	public static void loadSharedPreferences(Context context, Boolean force) {
		
		/*
		 * Note that because normal applications is trapped inside their own little sandbox, 
		 * they cannot make global changes. Because of this, the application (Settings) will not get the same
		 * version of this Common class as the module. mPreferences will therefore contain separate instances
		 * for the module and the application. This is no problem, but it is good to remember, especially 
		 * if one was to try to use this class to communicate between the two parts. The values in the shared preferences however
		 * is the same as this is controlled by Android. 
		 */
		
		if (mPreferences == null || force) {
			log("Loading SharedPreferences");
			
			if (context == null) {
				XSharedPreferences sharedPreferences = new XSharedPreferences(PACKAGE_NAME, HOOK_PREFERENCES);
				sharedPreferences.makeWorldReadable();
				
				mPreferences = sharedPreferences;
				
			} else {
				mPreferences = context.getApplicationContext().getSharedPreferences(HOOK_PREFERENCES, Context.MODE_WORLD_READABLE);
			}
		}
	}
	
	public static SharedPreferences getSharedPreferences(Context context) {
		if (mPreferences == null) {
			loadSharedPreferences(context);
		}

		return mPreferences;
	}
	
	public static void updateListSummary(ListPreference preference, String[] entryValues, String[] entrySummaries) {
		String curValue = preference.getValue();
		
		if (curValue != null) {
			for (int i=0; i < entryValues.length; i++) {
				if (entryValues[i].equals(curValue)) {
					preference.setSummary(entrySummaries[i]); break;
				}
			}
		}
	}
	
	@SuppressLint("NewApi")
	public static String keycodeToString(Integer keyCode) {
		/*
		 * KeyEvent to string is not supported in Gingerbread, 
		 * so we define the most basics ourself.
		 */
		
		if (keyCode != null) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_ZOOM_OUT: return "zoom Out";
				case KeyEvent.KEYCODE_ZOOM_IN: return "Zoom In";
				case KeyEvent.KEYCODE_VOLUME_UP: return "Volume Up";
				case KeyEvent.KEYCODE_VOLUME_DOWN: return "Volume Down";
				case KeyEvent.KEYCODE_VOLUME_MUTE: return "Volume Mute";
				case KeyEvent.KEYCODE_SETTINGS: return "Settings";
				case KeyEvent.KEYCODE_SEARCH: return "Search";
				case KeyEvent.KEYCODE_POWER: return "Power";
				case KeyEvent.KEYCODE_NOTIFICATION: return "Notification";
				case KeyEvent.KEYCODE_MUTE: return "Mute";
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
				case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return "Media Fast Farward";
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
		
		return "Unknown";
	}
	
	public static void requestConfigUpdate(Context context) {
		Intent intent = new Intent(Common.BroadcastOptions.INTENT_ACTION_REQUEST);
		intent.putExtra("request", Common.BroadcastOptions.REQUEST_RELOAD_CONFIGS);
		
		context.sendBroadcast(intent);
		
		Toast.makeText(context, R.string.config_updated, Toast.LENGTH_SHORT).show();
	}
	
	public static Integer generateKeyCode(int primary, int secondary) {
		/*
		 * This will merge to key codes into one single code containing both.
		 * 
		 * In order to avoid conflicts with internal Android key codes,
		 * the result will never go below 1000. 
		 * 
		 * primary(168) and secondary(211) will output 168211
		 * primary(85) and secondary(26) will output 8526
		 * primary(185) and secondary(6) will output 1856
		 * primary(6) and secondary(8) will output 6800
		 */
		if (secondary > 0) {
			int z = 1;
			
			do {
				z *= 10;
				
			} while ((secondary / z) > 0);
			
			int result = (primary * z) + secondary;
			
			if (result < 1000) {
				result *= (result < 100 ? 100 : 10);
			}
			
			return result;
		}
		
		return primary;
	}
	
	public static Integer extractKeyCode(int primary, int keycode) {
		if (keycode > 0 && keycode != primary) {
			/*
			 * This will extract the secondary key code from a merged code
			 * generated by generateKeyCode()
			 */
			int z = 100;
			
			while (z > 1 && ((double) keycode / (double) z) % 1.0D > 0D) {
				z /= 10;
			}
			
			int result = (int) keycode / z;
			int resultLength = result<100000 ? (result<10000 ? (result<1000 ? (result<100 ? (result<10 ? 1 : 2) : 3) : 4) : 5) : 6;
			int primaryLength = primary<100 ? (primary<10 ? 1 : 2) : 3;
			int length = resultLength - primaryLength;
			int y = length<3 ? (length<2 ? (length<1 ? 1 : 10) : 100) : 1000;
			
			return result - (primary * y);
		}
		
		return primary;
	}
	
	public static Boolean isUnlocked(Context context) {
		PackageManager manager = context.getPackageManager();
		
		return manager.checkSignatures(PACKAGE_NAME, PACKAGE_NAME_PRO)
	    		== PackageManager.SIGNATURE_MATCH;
	}
	
	public static class HapticFeedbackLw {
		private Vibrator mVibrator;
		private Context mContext;
		
		private long[] mLongpressPattern = null;
		private long[] mVirtualPattern = null;
		
		public HapticFeedbackLw(Context context) {
			mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
			mContext = context;
			
			for (int i=0; i < 2; i++) {
				int[] ar = context.getResources().getIntArray( i==0 ? com.android.internal.R.array.config_longPressVibePattern : com.android.internal.R.array.config_virtualKeyVibePattern );
				
				if (ar == null) {
					continue;
				}
				
				if (i == 0) mLongpressPattern = new long[ar.length];
				else mVirtualPattern = new long[ar.length];

				for (int x=0; x<ar.length; x++) {
					if (i == 0) mLongpressPattern[x] = ar[x];
					else mVirtualPattern[x] = ar[x];
				}
			}
		}
		
		public boolean vibrate(int effectId, boolean always) {
			long[] pattern = null;
			
	        switch (effectId) {
	            case HapticFeedbackConstants.LONG_PRESS:
	                pattern = mLongpressPattern;
	                break;
	            case HapticFeedbackConstants.VIRTUAL_KEY:
	                pattern = mVirtualPattern;
	                break;
	            default:
	                return false;
	        }
	        
	        return vibrate(pattern, always);
		}
		
		@SuppressLint("NewApi")
		public boolean vibrate(long[] pattern, boolean always) {
			if (android.os.Build.VERSION.SDK_INT >= 11 && !mVibrator.hasVibrator()) {
				return false;
			}
			
			final boolean hapticEnabled = android.os.Build.VERSION.SDK_INT >= 17 ? 
					Settings.System.getIntForUser(mContext.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 0, UserHandle.USER_CURRENT) != 0: 
						 Settings.System.getInt(mContext.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0;

			try {
				if (!always && (!hapticEnabled || ((KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode())) {
					return false;
				}
				
		        mVibrator.vibrate(pattern, pattern.length==1 ? 0 : -1);
		        
		        return true;
				
			} catch (Throwable e) { e.printStackTrace(); }
			
			return false;
		}
	}
	
	public static void log(String msg) {
		if(DEBUG)log(PACKAGE_NAME, msg);
	}
	
	public static void log(String tag, String msg) {
		if(DEBUG)Log.d(tag, msg);
	}
}