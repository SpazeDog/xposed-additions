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


import android.annotation.TargetApi;
import android.os.Binder;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.StrictMode;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.MultiParcelable.ParcelHelper;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.xposed.additionsgb.backend.ApplicationLayout.RotationConfig;
import com.spazedog.xposed.additionsgb.backend.LogcatMonitor.LogcatEntry;
import com.spazedog.xposed.additionsgb.backend.PowerManager.PowerPlugConfig;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

import java.util.List;


public interface BackendProxy extends IInterface {

    String DESCRIPTOR = BackendProxy.class.getName();

    /*
     * =================================================
     * TRANSACTION CODES
     */

    int TRANSACTION_getVersion = IBinder.FIRST_CALL_TRANSACTION+1;
    int TRANSACTION_isActive = IBinder.FIRST_CALL_TRANSACTION+2;
    int TRANSACTION_isReady = IBinder.FIRST_CALL_TRANSACTION+3;
    int TRANSACTION_detachListener = IBinder.FIRST_CALL_TRANSACTION+4;
    int TRANSACTION_attachListener = IBinder.FIRST_CALL_TRANSACTION+5;
    int TRANSACTION_sendListenerMsg = IBinder.FIRST_CALL_TRANSACTION+6;
    int TRANSACTION_getDebugFlags = IBinder.FIRST_CALL_TRANSACTION+7;
    int TRANSACTION_getLogEntries = IBinder.FIRST_CALL_TRANSACTION+8;
    int TRANSACTION_getPowerConfig = IBinder.FIRST_CALL_TRANSACTION+9;
    int TRANSACTION_isOwnerLocked = IBinder.FIRST_CALL_TRANSACTION+10;
    int TRANSACTION_getRotationConfig = IBinder.FIRST_CALL_TRANSACTION+11;
    int TRANSACTION_registerFeature = IBinder.FIRST_CALL_TRANSACTION+12;
    int TRANSACTION_hasFeature = IBinder.FIRST_CALL_TRANSACTION+13;


    /*
     * =================================================
     * INTERFACE DEFINED METHODS
     */

    int getVersion() throws RemoteException;
    boolean isActive() throws RemoteException;
    boolean isReady() throws RemoteException;
    void detachListener(ListenerProxy proxy) throws RemoteException;
    void attachListener(ListenerProxy proxy) throws RemoteException;
    void sendListenerMsg(int type, HashBundle data) throws RemoteException;
    int getDebugFlags() throws RemoteException;
    List<LogcatEntry> getLogEntries() throws RemoteException;
    PowerPlugConfig getPowerConfig() throws RemoteException;
    boolean isOwnerLocked() throws RemoteException;
    RotationConfig getRotationConfig() throws RemoteException;
    void registerFeature(String name) throws RemoteException;
    boolean hasFeature(String name) throws RemoteException;

    /*
     * =================================================
     * SERVICE EXTENSION CLASS
     */

    abstract class Stub extends Binder implements BackendProxy {

        @TargetApi(VERSION_CODES.HONEYCOMB)
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static BackendProxy asInterface(IBinder binder) {
            if (binder != null) {
                IInterface localInterface = binder.queryLocalInterface(DESCRIPTOR);

                if (localInterface != null && localInterface instanceof BackendProxy) {
                    return (BackendProxy) localInterface;

                } else {
                    return new Proxy(binder);
                }
            }

            return null;
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int type, Parcel args, Parcel caller, int flags) throws RemoteException {
            if (type == INTERFACE_TRANSACTION) {
                caller.writeString(DESCRIPTOR);

            } else {
                args.enforceInterface(DESCRIPTOR);
                int pos = caller.dataPosition();

                if (caller != null) {
                    caller.writeNoException();
                }

                try {
                    switch (type) {
                        case TRANSACTION_getVersion: {
                            caller.writeInt( getVersion() );

                        } break; case TRANSACTION_isActive: {
                            caller.writeInt( isActive() ? 1 : 0 );

                        } break; case TRANSACTION_isReady: {
                            caller.writeInt( isReady() ? 1 : 0 );

                        } break; case TRANSACTION_detachListener: {
                            detachListener(ListenerProxy.Stub.asInterface(args.readStrongBinder()));

                        } break; case TRANSACTION_attachListener: {
                            attachListener(ListenerProxy.Stub.asInterface(args.readStrongBinder()));

                        } break; case TRANSACTION_sendListenerMsg: {
                            int argType = args.readInt();
                            HashBundle argData = null;

                            if (args.readInt() > 0) {
                                argData = (HashBundle) ParcelHelper.unparcelData(args, Utils.getAppClassLoader());
                            }

                            sendListenerMsg(argType, argData);

                        } break; case TRANSACTION_getDebugFlags: {
                            caller.writeInt( getDebugFlags() );

                        } break; case TRANSACTION_getLogEntries: {
                            ParcelHelper.parcelData(getLogEntries(), caller, 0);

                        } break; case TRANSACTION_getPowerConfig: {
                            ParcelHelper.parcelData(getPowerConfig(), caller, 0);

                        } break; case TRANSACTION_isOwnerLocked: {
                            caller.writeInt(isOwnerLocked() ? 1 : 0);

                        } break; case TRANSACTION_getRotationConfig: {
                            ParcelHelper.parcelData(getRotationConfig(), caller, 0);

                        } break; case TRANSACTION_registerFeature: {
                            registerFeature((String) args.readValue(Utils.getAppClassLoader()));

                        } break; case TRANSACTION_hasFeature: {
                            caller.writeInt( hasFeature( (String) args.readValue(Utils.getAppClassLoader()) ) ? 1 : 0 );

                        } break; default: {
                            return false;
                        }
                    }

                } catch (Exception e) {
                    if (caller != null) {
                        caller.setDataPosition(pos);
                        caller.writeException(e);

                    } else {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }

                        throw new RuntimeException(e);
                    }
                }
            }

            return true;
        }
    }


    /*
     * =================================================
     * SERVICE PROXY CLASS
     */

    class Proxy implements BackendProxy {

        private IBinder mBinder;

        @TargetApi(VERSION_CODES.HONEYCOMB)
        public Proxy(IBinder binder) {
            mBinder = binder;
        }

        @Override
        public IBinder asBinder() {
            return mBinder;
        }

        @Override
        public int getVersion() throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel callee = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                mBinder.transact(TRANSACTION_getVersion, args, callee, 0);
                callee.readException();

                return callee.readInt();

            } finally {
                args.recycle();
                callee.recycle();
            }
        }

        @Override
        public boolean isActive() throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel callee = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                mBinder.transact(TRANSACTION_isActive, args, callee, 0);
                callee.readException();

                return callee.readInt() > 0;

            } finally {
                args.recycle();
                callee.recycle();
            }
        }

        @Override
        public boolean isReady() throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel callee = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                mBinder.transact(TRANSACTION_isReady, args, callee, 0);
                callee.readException();

                return callee.readInt() > 0;

            } finally {
                args.recycle();
                callee.recycle();
            }
        }

        @Override
        public void detachListener(ListenerProxy proxy) throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel callee = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeStrongBinder(proxy != null ? proxy.asBinder() : null);
                mBinder.transact(TRANSACTION_detachListener, args, callee, 0);
                callee.readException();

            } finally {
                args.recycle();
                callee.recycle();
            }
        }

        @Override
        public void attachListener(ListenerProxy proxy) throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel callee = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeStrongBinder(proxy != null ? proxy.asBinder() : null);
                mBinder.transact(TRANSACTION_attachListener, args, callee, 0);
                callee.readException();

            } finally {
                args.recycle();
                callee.recycle();
            }
        }

        @Override
        public void sendListenerMsg(int type, HashBundle data) throws RemoteException {
            Parcel args = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeInt(type);

                if (data != null) {
                    args.writeInt(1);
                    ParcelHelper.parcelData(data, args, 0);

                } else {
                    args.writeInt(0);
                }

                mBinder.transact(TRANSACTION_sendListenerMsg, args, null, IBinder.FLAG_ONEWAY);

            } finally {
                args.recycle();
            }
        }

        @Override
        public int getDebugFlags() throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel callee = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                mBinder.transact(TRANSACTION_getDebugFlags, args, callee, 0);
                callee.readException();

                return callee.readInt();

            } finally {
                args.recycle();
                callee.recycle();
            }
        }

        @Override
        public List<LogcatEntry> getLogEntries() throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel callee = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                mBinder.transact(TRANSACTION_getLogEntries, args, callee, 0);
                callee.readException();

                return (List<LogcatEntry>) ParcelHelper.unparcelData(callee, Utils.getAppClassLoader());

            } finally {
                args.recycle();
                callee.recycle();
            }
        }

        @Override
        public PowerPlugConfig getPowerConfig() throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel caller = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                mBinder.transact(Stub.TRANSACTION_getPowerConfig, args, caller, 0);
                caller.readException();

                return (PowerPlugConfig) ParcelHelper.unparcelData(caller, Utils.getAppClassLoader());

            } finally {
                args.recycle();
                caller.recycle();
            }
        }

        @Override
        public boolean isOwnerLocked() throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel caller = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                mBinder.transact(Stub.TRANSACTION_isOwnerLocked, args, caller, 0);
                caller.readException();

                return caller.readInt() > 0;

            } finally {
                args.recycle();
                caller.recycle();
            }
        }

        @Override
        public RotationConfig getRotationConfig() throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel caller = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                mBinder.transact(Stub.TRANSACTION_getRotationConfig, args, caller, 0);
                caller.readException();

                return (RotationConfig) ParcelHelper.unparcelData(caller, Utils.getAppClassLoader());

            } finally {
                args.recycle();
                caller.recycle();
            }
        }

        @Override
        public void registerFeature(String name) throws RemoteException {
            Parcel args = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeValue(name);
                mBinder.transact(Stub.TRANSACTION_getRotationConfig, args, null, 0);

            } finally {
                args.recycle();
            }
        }

        @Override
        public boolean hasFeature(String name) throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel caller = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeValue(name);
                mBinder.transact(Stub.TRANSACTION_getRotationConfig, args, caller, 0);
                caller.readException();

                return caller.readInt() > 0;

            } finally {
                args.recycle();
                caller.recycle();
            }
        }
    }
}
