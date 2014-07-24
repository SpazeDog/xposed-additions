package com.spazedog.xposed.additionsgb.configs;

import com.spazedog.xposed.additionsgb.Common.PlaceHolder;

public class Settings {
	
	/*
	 * Setting Names
	 */
	public static final String REMAP_TIMEOUT_LONGPRESS = "remap_press_delay";
	public static final String REMAP_TIMEOUT_DOUBLECLICK = "remap_tap_delay";
	public static final String REMAP_TIMEOUT_HARD_RESET = "remap_timeout_hard_reset";
	
	public static final String USB_CONNECTION_PLUG = "usb_plug_action";
	public static final String USB_CONNECTION_UNPLUG = "usb_unplug_action";
	
	public static final String USB_CONNECTION_SWITCH_PLUG = "usb_plug_switch";
	public static final String USB_CONNECTION_SWITCH_UNPLUG = "usb_unplug_switch";
	public static final String LAYOUT_ENABLE_GLOBAL_ROTATION = "layout_rotation_switch";
	public static final String DEBUG_ENABLE_LOGGING = "enable_debug";
	public static final String REMAP_ALLOW_EXTERNALS = "remap_allow_externals";
	public static final String REMAP_EXTERNALS_LIST = "remap_externals_list";
	
	public static final String LAYOUT_GLOBAL_ROTATION_BLACKLIST = "layout_rotation_blacklist";
	public static final String REMAP_LIST_KEYS = "remap_keys";
	public static final String REMAP_LIST_FORCED_HAPTIC = "forced_haptic_keys";
	
	/*
	 * Group Settings Names
	 */
	public static final String REMAP_KEY_ENABLE_CALLBTN = "remap_call_button";
	public static final String REMAP_KEY_LIST_CONDITIONS = "remap_key_conditions";
	public static final PlaceHolder REMAP_KEY_LIST_ACTIONS = new PlaceHolder("remap_key_actions:%1$s");
	
}
