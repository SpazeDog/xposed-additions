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


import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

import com.spazedog.lib.utilsLib.MultiParcelable.ParcelHelper;
import com.spazedog.xposed.additionsgb.utils.Utils;

public interface SystemStateProxy extends IInterface {

    public interface SystemState extends Parcelable {
        public boolean isScreenLocked();
        public boolean isScreenOn();
        public boolean isMusicPlaying();
        public boolean isPhoneInCall();
        public boolean isPhoneRinging();
        public boolean isKeyboardShowing();
        public String getFocusedPackageName();
        public int getUserId();
    }

    String DESCRIPTOR = SystemStateProxy.class.getName();


    /*
     * =================================================
     * TRANSACTION CODES
     */

    int TRANSACTION_getSystemState = IBinder.FIRST_CALL_TRANSACTION+1;
    int TRANSACTION_addStateListener = IBinder.FIRST_CALL_TRANSACTION+2;
    int TRANSACTION_removeStateListener = IBinder.FIRST_CALL_TRANSACTION+3;


    /*
     * =================================================
     * INTERFACE DEFINED METHODS
     */

    SystemState getSystemState() throws RemoteException;
    void addStateListener(StateListenerProxy proxy) throws RemoteException;
    void removeStateListener(StateListenerProxy proxy) throws RemoteException;

    /*
     * =================================================
     * SERVICE EXTENSION CLASS
     */

    abstract class Stub extends Binder implements SystemStateProxy {

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static SystemStateProxy asInterface(IBinder binder) {
            if (binder != null) {
                IInterface localInterface = binder.queryLocalInterface(DESCRIPTOR);

                if (localInterface != null && localInterface instanceof SystemStateProxy) {
                    return (SystemStateProxy) localInterface;

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

                if ((flags & IBinder.FLAG_ONEWAY) == 0 && caller != null) {
                    caller.writeNoException();
                }

                switch (type) {
                    case TRANSACTION_getSystemState: {
                        ParcelHelper.parcelData(getSystemState(), caller, 0);

                    } break; case TRANSACTION_addStateListener: {
                        addStateListener(StateListenerProxy.Stub.asInterface(args.readStrongBinder()));

                    } break; case TRANSACTION_removeStateListener: {
                        removeStateListener(StateListenerProxy.Stub.asInterface(args.readStrongBinder()));
                    }

                    default: return false;
                }
            }

            return true;
        }
    }


    /*
     * =================================================
     * SERVICE PROXY CLASS
     */

    class Proxy implements SystemStateProxy {

        private IBinder mBinder;

        public Proxy(IBinder binder) {
            mBinder = binder;
        }

        @Override
        public IBinder asBinder() {
            return mBinder;
        }

        @Override
        public SystemState getSystemState() throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel caller = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                mBinder.transact(TRANSACTION_getSystemState, args, caller, 0);
                caller.readException();

                return (SystemState) ParcelHelper.unparcelData(caller, Utils.getAppClassLoader());

            } finally {
                args.recycle();
                caller.recycle();
            }
        }

        @Override
        public void addStateListener(StateListenerProxy proxy) throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel callee = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeStrongBinder(proxy != null ? proxy.asBinder() : null);
                mBinder.transact(TRANSACTION_addStateListener, args, callee, 0);
                callee.readException();

            } finally {
                args.recycle();
                callee.recycle();
            }
        }

        @Override
        public void removeStateListener(StateListenerProxy proxy) throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel callee = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeStrongBinder(proxy != null ? proxy.asBinder() : null);
                mBinder.transact(TRANSACTION_removeStateListener, args, callee, 0);
                callee.readException();

            } finally {
                args.recycle();
                callee.recycle();
            }
        }
    }
}
