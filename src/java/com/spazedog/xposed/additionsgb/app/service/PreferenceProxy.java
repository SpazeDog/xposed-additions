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
 
package com.spazedog.xposed.additionsgb.app.service;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface PreferenceProxy extends IInterface {

	String DESCRIPTOR = PreferenceProxy.class.getName();


    /*
     * =================================================
     * TRANSACTION CODES
     */

    int TRANSACTION_putConfig = IBinder.FIRST_CALL_TRANSACTION+1;
    int TRANSACTION_getIntConfig = IBinder.FIRST_CALL_TRANSACTION+2;
    int TRANSACTION_getStringConfig = IBinder.FIRST_CALL_TRANSACTION+3;

    /*
     * =================================================
     * INTERFACE DEFINED METHODS
     */

    void putConfig(String name, Object value) throws RemoteException;
    int getIntConfig(String name) throws RemoteException;
    String getStringConfig(String name) throws RemoteException;

    /*
     * =================================================
     * SERVICE EXTENSION CLASS
     */
	
	abstract class Stub extends Binder implements PreferenceProxy {
		public Stub() {
			attachInterface(this, DESCRIPTOR);
		}
		
		public static PreferenceProxy asInterface(IBinder binder) {
			if (binder != null) {
				IInterface localInterface = binder.queryLocalInterface(DESCRIPTOR);
				
				if (localInterface != null && localInterface instanceof PreferenceProxy) {
					return (PreferenceProxy) localInterface;
					
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
                    case TRANSACTION_putConfig: {
                        String name = args.readString();
                        Object value = args.readValue(null);

                        putConfig(name, value);

                    } break; case TRANSACTION_getIntConfig: {
                        String name = args.readString();
                        int value = getIntConfig(name);

                        caller.writeInt(value);

                    } break; case TRANSACTION_getStringConfig: {
                        String name = args.readString();
                        String value = getStringConfig(name);

                        caller.writeString(value);

                    } break; default: {
                        return false;
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
	
	class Proxy implements PreferenceProxy {
		
		private IBinder mBinder;
		
		public Proxy(IBinder binder) {
			mBinder = binder;
		}

		@Override
		public IBinder asBinder() {
			return mBinder;
		}

        @Override
        public void putConfig(String name, Object value) throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel caller = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeValue(value);
                mBinder.transact(Stub.TRANSACTION_putConfig, args, caller, 0);
                caller.readException();

            } finally {
                args.recycle();
                caller.recycle();
            }
        }

        @Override
        public int getIntConfig(String name) throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel caller = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeString(name);
                mBinder.transact(Stub.TRANSACTION_getIntConfig, args, caller, 0);
                caller.readException();

                return caller.readInt();

            } finally {
                args.recycle();
                caller.recycle();
            }
        }

        @Override
        public String getStringConfig(String name) throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel caller = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeString(name);
                mBinder.transact(Stub.TRANSACTION_getStringConfig, args, caller, 0);
                caller.readException();

                return caller.readString();

            } finally {
                args.recycle();
                caller.recycle();
            }
        }
    }
}
