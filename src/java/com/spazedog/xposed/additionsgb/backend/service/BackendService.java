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

package com.spazedog.xposed.additionsgb.backend.service;


import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.lib.utilsLib.os.ThreadHandler;
import com.spazedog.xposed.additionsgb.app.service.PreferenceProxy;
import com.spazedog.xposed.additionsgb.backend.ApplicationLayout.RotationConfig;
import com.spazedog.xposed.additionsgb.backend.LogcatMonitor;
import com.spazedog.xposed.additionsgb.backend.LogcatMonitor.LogcatEntry;
import com.spazedog.xposed.additionsgb.backend.PowerManager.PowerPlugConfig;
import com.spazedog.xposed.additionsgb.utils.AndroidHelper;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;
import com.spazedog.xposed.additionsgb.utils.Utils.Type;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;

public class BackendService extends BackendProxy.Stub {
    public static final String TAG = BackendService.class.getName();

    public static final int FLAG_RELOAD_ALL = 0x00000001;
    public static final int FLAG_RELOAD_CONFIG = 0x00000001;

    private static final int STATE_ACTIVE = 1;
    private static final int STATE_READY = 2;

    private static class StateValues {
        public int UserId = 0;
        public int DebugFlags = 0;
        public boolean OwnerLock = false;
        public PowerPlugConfig PowerConfig;
        public RotationConfig RotationConfig;
    }

    private List<LogcatEntry> mLogEntries = new SparseList<LogcatEntry>();

    private int mSystemUID = Process.SYSTEM_UID;
    private int mAppUID = 0;
    private int mVersion = 0;

    private int mState = 0;

    private Context mContext;
    private List<String> mFeatureList = new SparseList<String>();
    private StateValues mValues = new StateValues();

    private BackendService() {}


    /*
     * =================================================
     * SETTINGS UP HANDLERS
     *
     *  - Handler Leaks does not matter as this instance will
     *  - continue to live until the device is shut down.
     */

    private Handler mListenerHandler;

    @SuppressLint("HandlerLeak")
    private class ListenerHandler extends ThreadHandler {

        public ListenerHandler() {
            super("XposedAdditions: BackendService");
        }

        @Override
        public void handleMessage(Message msg) {
            HashBundle data = (HashBundle) msg.obj;
            boolean system = msg.arg1 > 0;
            int type = msg.what;

            switch (type) {
                case Constants.BRC_SL_USERSWITCH: {
                    if (data.getBoolean("switched", false)) {
                        mValues.UserId = data.getInt("current_userid", 0);
                        sendPreferenceRequest(FLAG_RELOAD_ALL);
                    }

                } break; case Constants.BRC_LOGCAT: {
                    synchronized (mLogEntries) {
                        LogcatEntry entry = (LogcatEntry) data.getParcelable("entry");

                        if (entry != null) {
                            if (mLogEntries.size() > 150) {
                                /*
                                 * Truncate the list. We remove 15% of Constants.LOG_ENTRY_SIZE entries at a time to avoid having to do this each time this is called
                                 */
                                int truncate = (int) (Constants.LOG_ENTRY_SIZE * 0.15);

                                for (int i = 0; i < truncate; i++) {
                                    mLogEntries.remove(0);
                                }
                            }

                            mLogEntries.add(entry);

                        } else {
                            Utils.log(Level.WARNING, TAG, "Received empty logcat entry\n\t\tData Size: " + data.size());
                        }
                    }

                } break; case Constants.BRC_SERVICE_RELOAD: {
                    int flags = data.getInt("flags", FLAG_RELOAD_ALL);
                    sendPreferenceRequest(flags);

                    // This should not be parsed to listeners
                    return;
                }
            }

            for (ListenerMonitor curMonitor : mListeners) {
                try {
                    ListenerProxy proxy = curMonitor.getProxy();
                    IBinder binder = proxy.asBinder();

                    if (binder != null && binder.pingBinder()) {
                        proxy.onReceiveMsg(type, data);
                    }

                } catch (RemoteException e) {}
            }
        }
    };


    /*
     * =================================================
     * CONFIGURING THE BACKEND SERVICE
     */

    public static void init(Context context) {
        Utils.log(Level.INFO, TAG, "Instantiating Backend Service");

        BackendService serviceInstance = new BackendService();

        try {
            ReflectClass activityManager = ReflectClass.fromName("com.android.server.am.ActivityManagerService");
            activityManager.bridge("systemReady", serviceInstance.hook_onReady);

            serviceInstance.configure(context);

        } catch (ReflectException e) {
            Utils.log(Level.ERROR, TAG, e.getMessage(), e);
        }
    }

    private void configure(Context context) {
        Utils.log(Level.INFO, TAG, "Starting Settings Service");

        mContext = context;
        mListenerHandler = new ListenerHandler();

		/*
		 * Add this service to the service manager
		 */
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                ReflectClass.fromName("android.os.ServiceManager")
                        .invokeMethod("addService", Constants.SERVICE_MODULE_BACKEND, this);

            } else {
                ReflectClass.fromName("android.os.ServiceManager")
                        .invokeMethod("addService", Constants.SERVICE_MODULE_BACKEND, this, true);
            }

        } catch (ReflectException e) {
            Utils.log(Level.ERROR, TAG, e.getMessage(), e);
        }
    }


    /*
     * =================================================
     * SERVICE HOOKS
     */

    private MethodBridge hook_onReady = new MethodBridge() {
        @Override
        public void bridgeEnd(BridgeParams param) {
            Utils.log(Level.INFO, TAG, "Finalizig Settings Service");

            try {
				/*
				 * Get the app uid for access check usage
				 */
                PackageInfo info = mContext.getPackageManager().getPackageInfo(Constants.PACKAGE_NAME, 0);

                mAppUID = info.applicationInfo.uid;
                mVersion = info.versionCode;

            } catch (NameNotFoundException e) {
                Utils.log(Level.ERROR, TAG, "Could not find module package information", e);
            }

			/*
		 	 * Move temp log to this instance
		 	 */
            synchronized (mLogEntries) {
                for (LogcatEntry entry : LogcatMonitor.getLogEntries(true)) {
                    mLogEntries.add(entry);
                }
            }

            /*
             * The service is now accessible
             */
            mState = STATE_ACTIVE;

            sendPreferenceRequest(FLAG_RELOAD_ALL);
        }
    };


    /*
     * =================================================
     * INTERNAL TOOLS
     */

    private boolean checkPermission(String permission) {
        int uid = Binder.getCallingUid();

        return uid == mSystemUID || uid == mAppUID ||
                (permission != null && mContext.checkCallingPermission(permission) == PackageManager.PERMISSION_GRANTED);
    }

    private void sendPreferenceRequest(final int flags) {
        /*
         * If settings has been locked by owner, do not
         * update anything unless the current user is the owner
         */
        if (mValues.OwnerLock && mValues.UserId != 0) {
            return;
        }

		/*
		 * Make sure that our application is the one being called.
		 */
        Intent intent = new Intent(Constants.SERVICE_APP_PREFERENCES);
        intent.setPackage(Constants.PACKAGE_NAME);

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Utils.log(Type.SERVICES, Level.DEBUG, TAG, "Sending Application Preference Request with flags(" + flags + ")");

                if (mState == STATE_ACTIVE) {
                    mState = STATE_READY;
                }

                try {
                    PreferenceProxy proxy = PreferenceProxy.Stub.asInterface(service);
                    HashBundle data = new HashBundle();
                    data.put("flags", flags);

                    if ((flags & FLAG_RELOAD_CONFIG) != 0) {
                        int powerPlug = proxy.getIntConfig("power_plug", PowerPlugConfig.PLUGGED_DEFAULT);
                        int powerUnplug = proxy.getIntConfig("power_unplug", PowerPlugConfig.PLUGGED_DEFAULT);
                        boolean rotationOverwrite = proxy.getIntConfig("rotation_overwrite", 0) > 0;
                        List<String> rotationBlacklist = proxy.getStringListConfig("rotation_blacklist", null);

                        mValues.PowerConfig = new PowerPlugConfig(powerPlug, powerUnplug);
                        mValues.RotationConfig = new RotationConfig(rotationOverwrite, rotationBlacklist);

                        if (mValues.UserId == 0) {
                            mValues.DebugFlags = proxy.getIntConfig("debug_flags", Constants.FORCE_DEBUG ? Type.ALL : Type.DISABLED);
                            mValues.OwnerLock = proxy.getIntConfig("owner_lock", 0) > 0;
                        }

                        data.put("ownerLocked", mValues.OwnerLock);
                        data.put("debugFlags", mValues.DebugFlags);
                        data.put("powerConfig", mValues.PowerConfig);
                        data.put("rotationConfig", mValues.RotationConfig);
                    }

                    sendListenerMsg(-1, data, true);

                } catch (RemoteException e) {
                    Utils.log(Level.INFO, TAG, e.getMessage(), e);

                } finally {
                    mContext.unbindService(this);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };

        if (!AndroidHelper.bindService(mContext, intent, connection, Context.BIND_AUTO_CREATE, mValues.UserId)) {
            Utils.log(Level.ERROR, TAG, "Tried to send Application Preference Request with flags(" + flags + "), but the Preference Service is not available");
        }
    }


    /*
     * =================================================
     * LISTENERS
     */

    private final Set<ListenerMonitor> mListeners = new CopyOnWriteArraySet<ListenerMonitor>();

    private class ListenerMonitor implements IBinder.DeathRecipient {

        ListenerProxy mProxy;

        public ListenerMonitor(ListenerProxy proxy) throws RemoteException {
            mProxy = proxy;

            IBinder binder = mProxy.asBinder();
            if (binder != mProxy) {
                binder.linkToDeath(this, 0);
            }
        }

        @Override
        public void binderDied() {
            try {
                detachListener(mProxy);

            } catch (RemoteException e) {}
        }

        public void release() {
            IBinder binder = mProxy.asBinder();
            if (binder != null) {
                binder.unlinkToDeath(this, 0);
            }

            mProxy = null;
        }

        public ListenerProxy getProxy() {
            return mProxy;
        }

        public IBinder getBinder() {
            return mProxy.asBinder();
        }
    }

    @Override
    public void detachListener(ListenerProxy proxy) throws RemoteException {
        synchronized (mListeners) {
            IBinder binder = proxy.asBinder();
            ListenerMonitor monitor = null;

            for (ListenerMonitor curMonitor : mListeners) {
                if (curMonitor.getBinder() == binder) {
                    monitor = curMonitor; break;
                }
            }

            if (monitor != null && mListeners.remove(monitor)) {
                monitor.release();
            }
        }
    }

    @Override
    public void attachListener(ListenerProxy proxy) throws RemoteException {
        synchronized (mListeners) {
            IBinder binder = proxy.asBinder();

            for (ListenerMonitor curMonitor : mListeners) {
                if (curMonitor.getBinder() == binder) {
                    return;
                }
            }

            mListeners.add( new ListenerMonitor(proxy) );
        }
    }

    @Override
    public void sendListenerMsg(int type, HashBundle data) throws RemoteException {
        sendListenerMsg(type, data, false);
    }

    private void sendListenerMsg(int type, HashBundle data, boolean system) throws RemoteException {
        synchronized (mListeners) {
            if (type == -1 && !system) {
                Utils.log(Level.ERROR, TAG, "Only the service can send messages as service to it's listeners\n\t\tPID: " + Binder.getCallingPid() + "\n\t\tUID: " + Binder.getCallingUid() + "\n\t\tMsg Type: " + type);

            } else if (type < 0 && Binder.getCallingUid() != mSystemUID) {
                Utils.log(Level.ERROR, TAG, "Msg types below 0 can only be sent by the system\n\t\tPID: " + Binder.getCallingPid() + "\n\t\tUID: " + Binder.getCallingUid() + "\n\t\tMsg Type: " + type);

            } else if (type > 0 && !checkPermission(Constants.PERMISSION_SETTINGS_RW)) {
                Utils.log(Level.ERROR, TAG, "Msg types above 0 can only be sent by someone holding the permissions 'permissions.additionsgb.settings.rw'\n\t\tPID: " + Binder.getCallingPid() + "\n\t\tUID: " + Binder.getCallingUid() + "\n\t\tMsg Type: " + type);

            } else {
                mListenerHandler.obtainMessage(type, system ? 1 : 0, 0, data).sendToTarget();
            }
        }
    }


    /*
     * =================================================
     * INTERFACE DEFINED METHODS
     */

    @Override
    public int getVersion() throws RemoteException {
        return mVersion;
    }

    @Override
    public boolean isActive() throws RemoteException {
        return mState >= STATE_ACTIVE;
    }

    @Override
    public boolean isReady() throws RemoteException {
        return mState == STATE_READY;
    }

    @Override
    public int getDebugFlags() throws RemoteException {
        return mValues.DebugFlags;
    }

    @Override
    public List<LogcatEntry> getLogEntries() throws RemoteException {
        return mLogEntries;
    }

    @Override
    public PowerPlugConfig getPowerConfig() throws RemoteException {
        return mValues.PowerConfig;
    }

    @Override
    public boolean isOwnerLocked() throws RemoteException {
        return mValues.OwnerLock && mValues.UserId != 0;
    }

    @Override
    public RotationConfig getRotationConfig() throws RemoteException {
        return mValues.RotationConfig;
    }

    @Override
    public void registerFeature(String name) throws RemoteException {
        mFeatureList.add(name);
    }

    @Override
    public boolean hasFeature(String name) throws RemoteException {
        return mFeatureList.contains(name);
    }
}
