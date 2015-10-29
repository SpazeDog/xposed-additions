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


import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import com.spazedog.lib.utilsLib.HashBundle;

public interface ListenerProxy extends IInterface {

    String DESCRIPTOR = ListenerProxy.class.getName();


    /*
     * =================================================
     * TRANSACTION CODES
     */

    int TRANSACTION_onReceiveMsg = IBinder.FIRST_CALL_TRANSACTION+1;


    /*
     * =================================================
     * INTERFACE DEFINED METHODS
     */

    void onReceiveMsg(int type, HashBundle data) throws RemoteException;


    /*
     * =================================================
     * SERVICE EXTENSION CLASS
     */

    abstract class Stub extends Binder implements ListenerProxy {

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ListenerProxy asInterface(IBinder binder) {
            if (binder != null) {
                IInterface localInterface = binder.queryLocalInterface(DESCRIPTOR);

                if (localInterface != null && localInterface instanceof ListenerProxy) {
                    return (ListenerProxy) localInterface;

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
                    case TRANSACTION_onReceiveMsg: {
                        int argType = args.readInt();
                        HashBundle argData = null;

                        if (args.readInt() > 0) {
                            argData = args.readParcelable(HashBundle.class.getClassLoader());
                        }

                        onReceiveMsg(argType, argData);

                    } break;

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

    class Proxy implements ListenerProxy {

        private IBinder mBinder;

        public Proxy(IBinder binder) {
            mBinder = binder;
        }

        @Override
        public IBinder asBinder() {
            return mBinder;
        }

        @Override
        public void onReceiveMsg(int type, HashBundle data) throws RemoteException {
            Parcel args = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeInt(type);

                if (data != null) {
                    args.writeInt(1);
                    args.writeParcelable(data, 0);

                } else {
                    args.writeInt(0);
                }

                mBinder.transact(TRANSACTION_onReceiveMsg, args, null, IBinder.FLAG_ONEWAY);

            } finally {
                args.recycle();
            }
        }
    }
}
