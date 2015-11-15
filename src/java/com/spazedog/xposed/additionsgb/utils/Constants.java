/*
 * This file is part of the Xposed Additions Project: https://github.com/spazedog/xposed-additions
 *  
 * Copyright (c) 2015 Daniel Bergløv
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

package com.spazedog.xposed.additionsgb.utils;


import com.spazedog.lib.utilsLib.app.MsgActivity;
import com.spazedog.lib.utilsLib.app.logic.ActivityLogic;

public final class Constants {

    public static final String SUPPORT_EMAIL = "d.bergloev@gmail.com";
	public static final String PACKAGE_NAME = "com.spazedog.xposed.additionsgb";
	public static final String PERMISSION_SETTINGS_RW = "permissions.additionsgb.settings.rw";
	public static final String SERVICE_MODULE_BACKEND = "user.additionsgb.backend.service";
	public static final String SERVICE_APP_PREFERENCES = "app.additionsgb.preferences.service.BIND";
    public static final int LOG_ENTRY_SIZE = 150;

	public static final boolean FORCE_DEBUG = true;

    /*
     * Type codes from sendListenerMsg in the BackendService and Mgr
     */
    public static final int BRC_MGR_UPDATE = 512;           // Used by BackendServiceMgr to alert local listeners about config changes, properly triggered by 'BRC_SERVICE_RELOAD'

    public static final int BRC_SERVICE_RELOAD = 1;         // Request the service to reload settings from PreferenceService, used by BackendServiceMgr
    public static final int BRC_LOGCAT = 1024;              // New Logcat Entry sent by LogcatMonitor to the service
    public static final int BRC_SL_MEDIA = -1024;           // State update sent by SystemStateListener to the service
    public static final int BRC_SL_KEYGUARD = -1025;        // State update sent by SystemStateListener to the service
    public static final int BRC_SL_TELEPHONY = -1026;       // State update sent by SystemStateListener to the service
    public static final int BRC_SL_USERSWITCH = -1027;      // State update sent by SystemStateListener to the service
    public static final int BRC_SL_APPFOCUS = -1028;        // State update sent by SystemStateListener to the service
    public static final int BRC_SL_SOFTKEYBOARD = -1029;    // State update sent by SystemStateListener to the service
    public static final int BRC_SL_DISPLAY = -1030;         // State update sent by SystemStateListener to the service

    /**
     * Type codes from 'sendMessage', the internal Activity/Fragment message system from UtilsLib
     */
    public static final int MSG_ACTIVITY_RESULT = MsgActivity.MSG_ACTIVITY_RESULT;
    public static final int MSG_BACKSTACK_CHANGE = MsgActivity.MSG_BACKSTACK_CHANGE;
    public static final int MSG_FRAGMENT_ATTACHMENT = MsgActivity.MSG_FRAGMENT_ATTACHMENT;
    public static final int MSG_FRAGMENT_DETACHMENT = MsgActivity.MSG_FRAGMENT_DETACHMENT;
    public static final int MSG_NAVIGATION_DRAWER_STATE = 1;
    public static final int MSG_DIALOG_SELECTOR = 2;
    public static final int MSG_DIALOG_KEY_RECEIVER = 3;

	/*
	 * Result codes from 'startActivityForResult'
	 */
	public static final int RESULT_ACTION_PARSE_SHORTCUT = 1024;											// Used by FragmentLaunchSelector
}
