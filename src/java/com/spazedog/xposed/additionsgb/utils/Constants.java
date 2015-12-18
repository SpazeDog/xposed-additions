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

public final class Constants {

    public static final String SUPPORT_EMAIL = "d.bergloev@gmail.com";
    public static final String PACKAGE_NAME = "com.spazedog.xposed.additionsgb";
    public static final String PERMISSION_SETTINGS_RW = "permissions.additionsgb.settings.rw";
    public static final String SERVICE_MODULE_BACKEND = "user.additionsgb.backend.service";
    public static final String SERVICE_APP_PREFERENCES = "app.additionsgb.preferences.service.BIND";
    public static final int LOG_ENTRY_SIZE = 250;

    public static final boolean FORCE_DEBUG = false;

    /*
     * Type codes from sendListenerMsg in the BackendService and Mgr
     */
    public static final int BRC_LOGCAT = 0;                 // New Logcat Entry sent by LogcatMonitor to the service. Uses a special type number without permission checks since logs can be sent from anywhere

    public static final int BRC_SERVICE_RELOAD = 1;         // Request the service to reload settings from PreferenceService, used by BackendServiceMgr
    public static final int BRC_PWM_EVENT_REQUEST = 2;      // Request PWM to send all keys pressed via the backend msg service to allow capturing them
    public static final int BRC_PWM_EVENT_RESPONSE = -2;    // Response from PWM to the request 'BRC_PWM_EVENT_REQUEST'
    public static final int BRC_ATTACH_PROXY = -10;         // Used for now by SystemStateMonitor to attach itself to the service


    /**
     * Type codes from 'sendMessage', the internal Activity/Fragment message system from UtilsLib
     */
    public static final int MSG_ACTIVITY_RESULT = MsgActivity.MSG_ACTIVITY_RESULT;
    public static final int MSG_BACKSTACK_CHANGE = MsgActivity.MSG_BACKSTACK_CHANGE;
    public static final int MSG_FRAGMENT_ATTACHMENT = MsgActivity.MSG_FRAGMENT_ATTACHMENT;
    public static final int MSG_FRAGMENT_DETACHMENT = MsgActivity.MSG_FRAGMENT_DETACHMENT;
    public static final int MSG_NAVIGATION_DRAWER_STATE = 1;
    public static final int MSG_DIALOG_SELECTER = 2;
    public static final int MSG_DIALOG_KEY_RECEIVER = 3;

    /*
     * Result codes from 'startActivityForResult'
     */
    public static final int RESULT_ACTION_PARSE_SHORTCUT = 1024;											// Used by FragmentLaunchSelector
}