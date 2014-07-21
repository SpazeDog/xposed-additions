package com.spazedog.xposed.additionsgb.configs;

import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.KeyEvent;

import com.spazedog.xposed.additionsgb.Common.RemapAction;
import com.spazedog.xposed.additionsgb.Common.RemapAction.Validate;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class Actions {
	public final static List<RemapAction> COLLECTION = new ArrayList<RemapAction>();
	
	static {
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_POWER, 0, 0, 0, 0, 0));
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_HOME, 0, 0, 0, 0, 0, "off", "guard")); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_MENU, 0, 0, 0, 0, 0, "off", "guard")); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_BACK, 0, 0, 0, 0, 0, "off", "guard"));
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_SEARCH, 0, 0, 0, 0, 0, "off"));
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_CAMERA, 0, 0, 0, 0, R.string.selector_notice_camera_buttons, "off")); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_FOCUS, 0, 0, 0, 0, R.string.selector_notice_camera_buttons, "off", "guard"));
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_CALL, 0, 0, 0, 0, 0));
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_ENDCALL, 0, 0, 0, 0, 0));
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_MUTE, 0, 0, 0, 0, 0)); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0, 0, 0, 0, R.string.selector_notice_media_buttons)); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_MEDIA_NEXT, 0, 0, 0, 0, R.string.selector_notice_media_buttons));
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0, 0, 0, 0, R.string.selector_notice_media_buttons));
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_DPAD_UP, 0, 0, 0, 0, 0, "off", "guard")); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_DPAD_DOWN, 0, 0, 0, 0, 0, "off", "guard")); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_DPAD_LEFT, 0, 0, 0, 0, 0, "off", "guard")); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0, 0, 0, 0, "off", "guard")); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_DPAD_CENTER, 0, 0, 0, 0, 0, "off", "guard")); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_PAGE_UP, 0, 0, 0, 0, 0, "off", "guard")); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_PAGE_DOWN, 0, 0, 0, 0, 0, "off", "guard")); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_HEADSETHOOK, 0, 0, 0, 0, 0));
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_VOLUME_UP, 0, 0, 0, 0, 0)); 
		COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_VOLUME_DOWN, 0, 0, 0, 0, 0)); 
		
		if (android.os.Build.VERSION.SDK_INT >= 11) {
			COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_VOLUME_MUTE, 11, 0, 0, 0, 0));
			COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_ZOOM_IN, 11, 0, 0, 0, 0, "off", "guard")); 
			COLLECTION.add(new RemapAction(KeyEvent.KEYCODE_ZOOM_OUT, 11, 0, 0, 0, 0, "off", "guard"));
		}
		
		COLLECTION.add(new RemapAction("disabled", 0, R.string.remap_title_disabled, R.string.remap_summary_disabled, 0, 0));
		COLLECTION.add(new RemapAction("guarddismiss", 0, R.string.remap_title_dismissguard, R.string.remap_summary_dismissguard, 0, 0, "off", "on"));
		COLLECTION.add(new RemapAction("previousapp", 0, R.string.remap_title_previous_app, R.string.remap_summary_previous_app, 0, 0, "off", "guard"));
		COLLECTION.add(new RemapAction("killapp", 0, R.string.remap_title_killapp, R.string.remap_summary_killapp, 0, 0, "off", "guard"));
		COLLECTION.add(new RemapAction("fliptoggle", 0, R.string.remap_title_fliptoggle, R.string.remap_summary_fliptoggle, 0, 0, "off"));
		COLLECTION.add(new RemapAction("flipleft", 11, R.string.remap_title_flipleft, R.string.remap_summary_flipleft, 0, 0, "off"));
		COLLECTION.add(new RemapAction("flipright", 11, R.string.remap_title_flipright, R.string.remap_summary_flipright, 0, 0, "off"));
		
		COLLECTION.add(new RemapAction("torch", 0, R.string.remap_title_torch, R.string.remap_summary_torch, R.string.selector_alert_missing_torch, 0, new Validate(){ 
			@Override
			public Boolean onValidate(Context context) { 
				return XServiceManager.getInstance().getBoolean("variable:remap.support.torch"); 
			} 
			
			@Override
			public Boolean onDisplayAlert(Context context) { 
				return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
			}
		}));
		
		COLLECTION.add(new RemapAction("powermenu", 0, R.string.remap_title_powermenu, R.string.remap_summary_powermenu, 0, 0, "off", new Validate(){ 
			@Override
			public Boolean onValidate(Context context) { 
				return XServiceManager.getInstance().getBoolean("variable:remap.support.global_actions"); 
			}
		}));
		
		COLLECTION.add(new RemapAction("recentapps", 0, R.string.remap_title_recentapps, R.string.remap_summary_recentapps, 0, 0, "off", new Validate(){ 
			@Override
			public Boolean onValidate(Context context) { 
				return XServiceManager.getInstance().getBoolean("variable:remap.support.recent_dialog"); 
			}
		}));
		
		COLLECTION.add(new RemapAction("screenshot", 0, R.string.remap_title_screenshot, R.string.remap_summary_screenshot, 0, 0, "off", new Validate(){ 
			@Override
			public Boolean onValidate(Context context) { 
				return XServiceManager.getInstance().getBoolean("variable:remap.support.screenshot"); 
			}
		}));
	}
}
