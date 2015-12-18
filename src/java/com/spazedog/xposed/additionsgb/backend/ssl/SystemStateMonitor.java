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

package com.spazedog.xposed.additionsgb.backend.ssl;


import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Parcel;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.ReflectMember.Match;
import com.spazedog.lib.reflecttools.ReflectMember.Result;
import com.spazedog.lib.reflecttools.ReflectMethod;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.MultiParcelableBuilder;
import com.spazedog.lib.utilsLib.os.ProxyStorage;
import com.spazedog.lib.utilsLib.os.ProxyStorage.ProxyWrapper;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;
import com.spazedog.xposed.additionsgb.utils.Utils.Type;

/**
 * Keeping track of various states of a device is not something that comes easy on Android.
 * The few things that is possible, is very messy in the way that some things require broadcasts,
 * other things require listeners and some even different from that. But most of it is iether not implemented
 * or it is only available to the system.
 *
 * This class assembles all of it and adds hacks for the missing parts.
 * It creates a centralized way of keeping track of everything that this module needs.
 *
 * Since this class runs in the system process along with the module service, we did not really need to make this a proxy.
 * But seen as Android has a tendency to rapidly change it's structure with every release, we might as well start
 * preparing for the chance that the main process be split into smaller peaces. We might also some day find the need to
 * send a Binder elsewhere then just to the service. In any case, this class is ready for it. And as long as it is only sent
 * within the same process, the binder feature is never used, our Stub class will make sure of this.
 */
public class SystemStateMonitor extends SystemStateProxy.Stub {

    public static final String TAG = SystemStateMonitor.class.getName();

    @SuppressLint("ParcelCreator")
    protected static class SystemStateValues extends MultiParcelableBuilder implements SystemState {

        protected volatile boolean screenLocked = false;
        protected volatile boolean screenOn = true;
        protected volatile boolean musicPlaying = false;
        protected volatile boolean keyboardShowing = false;
        protected volatile int phoneState = 0;
        protected volatile int userId = 0;
        protected volatile String focusedPackage;

        protected SystemStateValues() {}

        public SystemStateValues(Parcel in, ClassLoader loader) {
            screenLocked = in.readInt() > 0;
            screenOn = in.readInt() > 0;
            musicPlaying = in.readInt() > 0;
            keyboardShowing = in.readInt() > 0;
            phoneState = in.readInt();
            userId = in.readInt();
            focusedPackage = (String) unparcelData(in, null);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);

            out.writeInt(screenLocked ? 1 : 0);
            out.writeInt(screenOn ? 1 : 0);
            out.writeInt(musicPlaying ? 1 : 0);
            out.writeInt(keyboardShowing ? 1 : 0);
            out.writeInt(phoneState);
            out.writeInt(userId);
            parcelData(focusedPackage, out, flags);
        }

        @Override
        public boolean isScreenLocked() {
            return screenLocked;
        }

        @Override
        public boolean isScreenOn() {
            return screenOn;
        }

        @Override
        public boolean isMusicPlaying() {
            return musicPlaying;
        }

        @Override
        public boolean isPhoneInCall() {
            return phoneState == TelephonyManager.CALL_STATE_OFFHOOK;
        }

        @Override
        public boolean isPhoneRinging() {
            return phoneState == TelephonyManager.CALL_STATE_RINGING;
        }

        @Override
        public boolean isKeyboardShowing() {
            return keyboardShowing;
        }

        @Override
        public String getFocusedPackageName() {
            return focusedPackage;
        }

        @Override
        public int getUserId() {
            return userId;
        }
    }

    private SystemStateValues mState = new SystemStateValues();
    private ProxyStorage<StateListenerProxy> mListeners = new ProxyStorage<StateListenerProxy>();

    private ReflectClass mAudioSystem;
    private ReflectClass mWindowManagerPolicy;
    private ReflectClass mWindowManagerService;
    private ReflectClass mActivityManagerService;
    private Context mContext;
    private boolean mReady = false;

    /*
     * =================================================
     * INTERNALS
     */

    /**
     *
     */
    private void sendStateChanged(int state) {
        for (ProxyWrapper<StateListenerProxy> wrapper : mListeners) {
            try {
                StateListenerProxy proxy = wrapper.getProxy();

                if (proxy != null) {
                    proxy.onStateChanged(state);
                }

            } catch (RemoteException e) {}
        }
    }


    /*
     * =================================================
     * INITIATE
     */

    /**
     *
     */
    public static void init(Context context) {
        Utils.log(Level.INFO, TAG, "Instantiating System State Monitor");

        SystemStateMonitor instance = new SystemStateMonitor();

        try {
            ReflectClass activityManager = ReflectClass.fromName("com.android.server.am.ActivityManagerService");
            activityManager.bridge("systemReady", instance.systemReady);
            activityManager.bridge("setFocusedActivityLocked", instance.applicationFocusedListener);

			/*
			 * Multi-user support was not added until Jellybean 4.2.
			 */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                activityManager.bridge("dispatchUserSwitch", instance.userSwitchListener);
            }

            ReflectClass inputManager = ReflectClass.fromName("com.android.server.InputMethodManagerService");
            inputManager.bridge("showCurrentInputLocked", instance.softKeyboardListener);
            inputManager.bridge("hideCurrentInputLocked", instance.softKeyboardListener);

            instance.configure(context);

        } catch (ReflectException e) {
            Utils.log(Level.ERROR, TAG, e.getMessage(), e);
        }
    }

    /**
     *
     */
    private void configure(Context context) {
        Utils.log(Level.INFO, TAG, "Configuring System State Monitor");

        mContext = context;

		/*
		 * Let's register ourself with the service
		 */
        BackendServiceMgr backendMgr = BackendServiceMgr.getInstance();

        if (backendMgr != null) {
            HashBundle data = new HashBundle();
            data.put("descriptor", getInterfaceDescriptor());
            data.put("binder", this);

            backendMgr.sendListenerMsg(Constants.BRC_ATTACH_PROXY, data);
        }
    }

    /**
     *
     */
    protected MethodBridge systemReady = new MethodBridge() {
        @Override
        public void bridgeEnd(BridgeParams param) {
            try {
                Utils.log(Level.INFO, TAG, "Starting all System State Listeners");

                /*
                 * Static native/jvm bridge to keep track of audio
                 */
                mAudioSystem = ReflectClass.fromName("android.media.AudioSystem");

                /*
                 * Add the ActivityManagerInstance to the Receiver
                 */
                mActivityManagerService = ReflectClass.fromReceiver(param.receiver);
                mWindowManagerService = (ReflectClass) mActivityManagerService.findField("mWindowManager").getValue(Result.INSTANCE);
                mWindowManagerPolicy = (ReflectClass) mWindowManagerService.findField("mPolicy").getValue(Result.INSTANCE);

                /*
                 * Add telephony listener
                 */
                TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
                telephonyManager.listen(telephonyListener, PhoneStateListener.LISTEN_CALL_STATE);

                /*
                 * Start the listener worker thread to monitor media events
                 */
                stateMonitor.start();

                /*
                 * Setup Android broadcast actions
                 */
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_SCREEN_ON);
                filter.addAction(Intent.ACTION_SCREEN_OFF);

                mContext.registerReceiver(stateBroadcastReveiver, filter);
                mReady = true;

            } catch (ReflectException e) {
                Utils.log(Level.ERROR, TAG, e.getMessage(), e);
            }
        }
    };

    /*
     * =================================================
     * MONITORS
     */

    /**
     * Worker that will monitor media streaming states.
     */
    protected Thread stateMonitor = new Thread("stateMonitor$Worker") {

        public static final int STREAM_MUSIC = 3;

        public void run() {
            Utils.log(Level.INFO, TAG, "Media listener worker is started");

            boolean newStreamApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
            ReflectMethod keyguardMethod = mWindowManagerPolicy.findMethod("inKeyguardRestrictedKeyInputMode");
            ReflectMethod streamCheckMethod = null;

            if (newStreamApi) {
                streamCheckMethod = mAudioSystem.findMethod("isStreamActive", Match.BEST, Integer.TYPE, Integer.TYPE);

            } else {
                streamCheckMethod = mAudioSystem.findMethod("isStreamActive", Match.BEST, Integer.TYPE);
            }

            boolean isMusicPlaying;
            boolean inKeyguard;

            while (true) {
                try {
                    if (newStreamApi) {
                        isMusicPlaying = (Boolean) streamCheckMethod.invoke(STREAM_MUSIC, 0);

                    } else {
                        isMusicPlaying = (Boolean) streamCheckMethod.invoke(STREAM_MUSIC);
                    }

                    if (isMusicPlaying != mState.musicPlaying) {
                        mState.musicPlaying = isMusicPlaying;

                        Utils.log(Type.STATE, Level.DEBUG, TAG, "Alert about media change\n\t\tMusic = " + (isMusicPlaying ? "On" : "Off"));

                        sendStateChanged(StateListenerProxy.STATE_MEDIA_PLAYBACK);
                    }

                    inKeyguard = (Boolean) keyguardMethod.invoke();

                    if (inKeyguard != mState.screenLocked) {
                        mState.screenLocked = inKeyguard;

                        Utils.log(Type.STATE, Level.DEBUG, TAG, "Alert about keyguard state change\n\t\tKeyguard = " + (inKeyguard ? "On" : "Off") + "");

                        sendStateChanged(StateListenerProxy.STATE_SCREEN_LOCK);
                    }

                    synchronized (this) {
                        wait(100);
                    }

                } catch (InterruptedException e) {}
            }
        }
    };

    /**
     * Listener to obtain any change the telephony state
     */
    protected PhoneStateListener telephonyListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (state != mState.phoneState) {
                mState.phoneState = state;

                Utils.log(Type.STATE, Level.DEBUG, TAG, "Alert about telephony change\n\t\tState = " + (state == TelephonyManager.CALL_STATE_RINGING ? "Ringing" : (state == TelephonyManager.CALL_STATE_OFFHOOK ? "InCall" : "Idle")));

                sendStateChanged(StateListenerProxy.STATE_PHONE_CALL);
            }
        }
    };

    /**
     * This listener will report when a user is switched by another.
     * It can be used to implement proper multi-user support by supplying
     * the type of information that Google should have had added themselves.
     * Or rather should not have made hidden or system protected.
     */
    protected MethodBridge userSwitchListener = new MethodBridge() {
        @Override
        public void bridgeBegin(BridgeParams param) {
            Utils.log(Type.STATE, Level.DEBUG, TAG, "Alert about an upcoming user switch\n\t\tCurrent User = " + param.args[1] + "\n\t\tNew User = " + param.args[2]);

            sendStateChanged(StateListenerProxy.STATE_ENDING_USER_SESSION);
        }

        @Override
        public void bridgeEnd(BridgeParams param) {
            mState.userId = (Integer) param.args[2];

            Utils.log(Type.STATE, Level.DEBUG, TAG, "Alert about a finished user switch\n\t\tOld User" + param.args[1] + "\n\t\tCurrent User" + param.args[2]);

            sendStateChanged(StateListenerProxy.STATE_STARTING_USER_SESSION);
        }
    };

    /**
     * This listener will report any change in package focus.
     * It can be used to keep track of the current used application.
     */
    protected MethodBridge applicationFocusedListener = new MethodBridge() {
        @Override
        public void bridgeEnd(BridgeParams param) {
            ReflectClass activityRecord = ReflectClass.fromReceiver(param.args[0]);
            ActivityInfo activityInfo = (ActivityInfo) activityRecord.findField("info").getValue();

            if (!activityInfo.packageName.equals(mState.focusedPackage)) {
                mState.focusedPackage = activityInfo.packageName;

                Utils.log(Type.STATE, Level.DEBUG, TAG, "Alert about application focus change\n\t\tIn Focus = " + activityInfo.packageName);

                sendStateChanged(StateListenerProxy.STATE_PACKAGE_FOCUS);
            }
        }
    };

    /**
     * This keeps track of the Soft Keyboard visibillity
     */
    protected MethodBridge softKeyboardListener = new MethodBridge() {

        boolean mIsShowing = false;

        @Override
        public void bridgeEnd(BridgeParams param) {
            /*
             * This is a simple check, did someone call 'showCurrentInputLocked' or 'hideCurrentInputLocked'
             */
            boolean isShowing = (Boolean) param.getResult() && param.method.getName().startsWith("show");

            if (isShowing != mState.keyboardShowing) {
                mState.keyboardShowing = isShowing;

                Utils.log(Type.STATE, Level.DEBUG, TAG, "Alert about Soft Keyboard visibillity change\n\t\tVisible = " + (isShowing ? "Yes" : "No"));

                sendStateChanged(StateListenerProxy.STATE_KEYBOARD_VISIBILITY);
            }
        }
    };

    /**
     * This receiver is used for everything that can be handled without hooks
     */
    protected BroadcastReceiver stateBroadcastReveiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF) || intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                boolean screenOn = intent.getAction().equals(Intent.ACTION_SCREEN_ON);

                if (screenOn != mState.screenOn) {
                    mState.screenOn = screenOn;

                    Utils.log(Type.STATE, Level.DEBUG, TAG, "Alert about screen state change\n\t\tScreen = " + (screenOn ? "On" : "Off"));

                    sendStateChanged(StateListenerProxy.STATE_SCREEN_POWER);
                }
            }
        }
    };


    /*
     * =================================================
     * INTERFACE OVERWRITES
     */

    /**
     *
     */
    @Override
    public SystemState getSystemState() throws RemoteException {
        return mState;
    }

    /**
     *
     */
    @Override
    public void addStateListener(StateListenerProxy proxy) throws RemoteException {
        mListeners.add(proxy);
    }

    /**
     *
     */
    @Override
    public void removeStateListener(StateListenerProxy proxy) throws RemoteException {
        mListeners.remove(proxy);
    }
}
