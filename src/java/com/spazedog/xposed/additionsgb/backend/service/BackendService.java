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


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.xposed.additionsgb.app.service.PreferenceProxy;
import com.spazedog.xposed.additionsgb.backend.LogcatMonitor;
import com.spazedog.xposed.additionsgb.backend.LogcatMonitor.LogcatEntry;
import com.spazedog.xposed.additionsgb.utils.AndroidHelper;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BackendService extends BackendProxy.Stub {
    public static final String TAG = BackendService.class.getName();

    public static final int FLAG_PREPARE = 0x00000001;
    public static final int FLAG_CONFIG = 0x00000001;

    private static class StateValues {
        public int UserId = 0;
        public boolean DebugEnabled = false;
    }

    private List<LogcatEntry> mLogEntries = new SparseList<LogcatEntry>();

    private int mSystemUID = 0;
    private int mAppUID = 0;
    private int mVersion = 0;

    private boolean mIsReady = false;
    private boolean mIsActive = false;

    private Context mContext;
    private StateValues mValues = new StateValues();

    private BackendService() {}


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
            mIsActive = true;

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

                mSystemUID = Process.myUid();
                mAppUID = info.applicationInfo.uid;
                mVersion = info.versionCode;

            } catch (NameNotFoundException e) {
                Utils.log(Level.ERROR, TAG, "Could not find module package information", e);
            }

            sendPreferenceRequest(FLAG_PREPARE);
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
		 * Make sure that our application is the one being called.
		 */
        Intent intent = new Intent(Constants.SERVICE_APP_PREFERENCES);
        intent.setPackage(Constants.PACKAGE_NAME);

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Utils.log(Level.DEBUG, TAG, "Sending Application Preference Request with flags(" + flags + ")");

                try {
                    PreferenceProxy proxy = PreferenceProxy.Stub.asInterface(service);

                    if ((flags & FLAG_CONFIG) != 0)
                        mValues.DebugEnabled = proxy.getIntConfig("enable_debug") > 0 || Constants.FORCE_DEBUG;

                    if ((flags & FLAG_PREPARE) == FLAG_PREPARE) {
                        mIsReady = true;
                        sendListenerMsg(-1, null);
                    }

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

    private final Set<ListenerMonitor> mListeners = new HashSet<ListenerMonitor>();

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
        synchronized (mListeners) {
            switch (type) {
                case Constants.BRC_SL_USERSWITCH: {
                    if (data.getBoolean("switched", false)) {
                        mValues.UserId = data.getInt("current_userid", 0);
                        sendPreferenceRequest(FLAG_PREPARE);
                    }

                } break; case Constants.BRC_LOGCAT: {
                    synchronized (mLogEntries) {
                        if (mLogEntries.size() > 150) {
                            /*
                             * Truncate the list. We remove 15% of Constants.LOG_ENTRY_SIZE entries at a time to avoid having to do this each time this is called
                             */
                            int truncate = (int) (Constants.LOG_ENTRY_SIZE * 0.15);

                            for (int i=0; i < truncate; i++) {
                                mLogEntries.remove(0);
                            }
                        }

                        mLogEntries.add((LogcatEntry) data.getParcelable("entry"));
                    }
                }
            }

            for (ListenerMonitor curMonitor : mListeners) {
                try {
                    ListenerProxy proxy = curMonitor.getProxy();

                    if (proxy.asBinder().pingBinder()) {
                        proxy.onReceiveMsg(type, data);
                    }

                } catch (RemoteException e) {}
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
        return mIsActive;
    }

    @Override
    public boolean isReady() throws RemoteException {
        return mIsReady;
    }

    @Override
    public boolean isDebugEnabled() throws RemoteException {
        return mValues.DebugEnabled;
    }

    @Override
    public List<LogcatEntry> getLogEntries() throws RemoteException {
        return mLogEntries;
    }
}
