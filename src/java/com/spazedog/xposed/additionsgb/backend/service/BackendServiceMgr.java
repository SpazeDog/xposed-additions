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


import android.os.*;
import android.os.Process;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.xposed.additionsgb.backend.LogcatMonitor.LogcatEntry;
import com.spazedog.xposed.additionsgb.backend.PowerManager.PowerPlugConfig;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BackendServiceMgr {

    public static final String TAG = BackendServiceMgr.class.getName();


    /*
     * =================================================
     * INSTANCE CONTROL
     */

    private static WeakReference<BackendServiceMgr> oInstance = new WeakReference<BackendServiceMgr>(null);
    private BackendProxy mServiceProxy;

    public static BackendServiceMgr getInstance() {
        return getInstance(false);
    }

    public static BackendServiceMgr getInstance(boolean suppressLog) {
        synchronized(oInstance) {
            BackendServiceMgr instance = oInstance.get();

            if (instance == null) {
                try {
                    oInstance = new WeakReference<BackendServiceMgr>( (instance = new BackendServiceMgr()) );

                } catch (Exception e) {
                    if (!suppressLog) {
                        Utils.log(Level.ERROR, TAG, e.getMessage(), e);
                    }
                }
            }

            return instance;
        }
    }

    private BackendServiceMgr() throws Exception {
        try {
            ReflectClass service = ReflectClass.fromClass(BackendProxy.class).bindInterface(Constants.SERVICE_MODULE_BACKEND);

            if (service != null) {
                mServiceProxy = (BackendProxy) service.getReceiver();

                if (mServiceProxy != null) {
                    mServiceProxy.attachListener(mListenerProxy);

                } else {
                    throw new Exception("Could not bind to Backend Service");
                }

            } else {
                throw new Exception("Could not bind to Backend Service");
            }

        } catch (ReflectException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    private void handleRemoteException(RemoteException e) {
        synchronized(oInstance) {
            Utils.log(Level.DEBUG, TAG, "Service connection died. Establishing a new connection");

            try {
                try {
                    ReflectClass service = ReflectClass.fromClass(BackendProxy.class).bindInterface(Constants.SERVICE_MODULE_BACKEND);

                    if (service != null) {
                        mServiceProxy = (BackendProxy) service.getReceiver();

                    } else {
                        Utils.log(Level.ERROR, TAG, e.getMessage(), e);
                    }

                } catch (ReflectException ei) {
                    Utils.log(Level.ERROR, TAG, e.getMessage(), e);
                }

            } catch (Exception ei) {
                Utils.log(Level.ERROR, TAG, e.getMessage(), e);
            }
        }
    }


    /*
     * =================================================
     * SERVICE LISTENERS
     */

    private class ListenerHandler extends Handler {
        public ListenerHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            for (ServiceListener curListener : mListeners) {
                curListener.onReceiveMsg(msg.what, (HashBundle) msg.obj);
            }
        }
    }

    public Handler getHandler() {
        if (mListenerHandler == null && Looper.getMainLooper() != null) {
            mListenerHandler = new ListenerHandler();
        }

        return mListenerHandler;
    }

    private ListenerHandler mListenerHandler;
    private ListenerProxy mListenerProxy = new ListenerProxy.Stub() {

        @Override
        public void onReceiveMsg(int type, HashBundle data) {
            synchronized (mListeners) {
                /*
                 * -1 is everything sent directly from the service.
                 * For now it is only used to send updated configs,
                 * but it might be used for more in the future.
                 */
                if (type == -1 && Binder.getCallingUid() == Process.SYSTEM_UID) {
                    type = Constants.BRC_MGR_UPDATE;

                } else if (type == -1) {
                    Utils.log(Level.ERROR, TAG, "Received message from service outside the system process"); return;
                }

                Handler handler = getHandler();

                if (handler != null) {
                    handler.obtainMessage(type, data).sendToTarget();

                } else {
                    for (ServiceListener curListener : mListeners) {
                        curListener.onReceiveMsg(type, data);
                    }
                }
            }
        }
    };

    private final Set<ServiceListener> mListeners = new HashSet<ServiceListener>();

    public interface ServiceListener {
        void onReceiveMsg(int type, HashBundle data);
    }

    public void attachListener(ServiceListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    public void detachListener(ServiceListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }


    /*
     * =================================================
     * SERVICE CALLS
     */

    private int mServiceVersion = -1;
    private boolean mServiceIsActive = false;
    private boolean mServiceIsReady = false;

    public boolean isServiceActive() {
        if (!mServiceIsActive) {
            try {
                if (mServiceProxy.isActive()) {
                    mServiceIsActive = true;
                }

            } catch (RemoteException e) {
                handleRemoteException(e);

            } catch (NullPointerException e) {}
        }

        return mServiceIsActive;
    }

    public boolean isServiceReady() {
        if (!mServiceIsReady) {
            try {
                if (mServiceProxy.isReady()) {
                    mServiceIsReady = true;
                }

            } catch (RemoteException e) {
                handleRemoteException(e);

            } catch (NullPointerException e) {}
        }

        return mServiceIsReady;
    }

    public int getServiceVersion() {
        if (mServiceVersion == -1 && isServiceReady()) {
            try {
                mServiceVersion = mServiceProxy.getVersion();

            } catch (RemoteException e) {
                handleRemoteException(e);

            } catch (NullPointerException e) {}
        }

        return mServiceVersion;
    }

    public void sendListenerMsg(int type, HashBundle data) {
        try {
            mServiceProxy.sendListenerMsg(type, data);

        } catch (RemoteException e) {
            handleRemoteException(e);

        } catch (NullPointerException e) {}
    }

    public boolean isDebugEnabled() {
        try {
            return mServiceProxy.isDebugEnabled();

        } catch (RemoteException e) {
            handleRemoteException(e);

        } catch (NullPointerException e) {}

        return false;
    }

    public List<LogcatEntry> getLogEntries() {
        try {
            return mServiceProxy.getLogEntries();

        } catch (RemoteException e) {
            handleRemoteException(e);

        } catch (NullPointerException e) {}

        return null;
    }

    public void requestServiceReload() {
        requestServiceReload(BackendService.FLAG_RELOAD_ALL);
    }

    public void requestServiceReload(int flags) {
        sendListenerMsg(Constants.BRC_SERVICE_RELOAD, new HashBundle("flags", flags));
    }

    public PowerPlugConfig getPowerConfig() {
        try {
            return mServiceProxy.getPowerConfig();

        } catch (RemoteException e) {
            handleRemoteException(e);

        } catch (NullPointerException e) {}

        return null;
    }

    public boolean isOwnerLocked() {
        try {
            return mServiceProxy.isOwnerLocked();

        } catch (RemoteException e) {
            handleRemoteException(e);

        } catch (NullPointerException e) {}

        return false;
    }
}
