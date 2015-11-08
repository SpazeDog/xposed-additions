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

import com.spazedog.lib.utilsLib.MultiParcelable.ParcelHelper;

import java.util.List;

public interface PreferenceProxy extends IInterface {

	String DESCRIPTOR = PreferenceProxy.class.getName();


    /*
     * =================================================
     * TRANSACTION CODES
     */

    int TRANSACTION_putConfig = IBinder.FIRST_CALL_TRANSACTION+1;
    int TRANSACTION_getIntConfig = IBinder.FIRST_CALL_TRANSACTION+2;
    int TRANSACTION_getStringConfig = IBinder.FIRST_CALL_TRANSACTION+3;
    int TRANSACTION_getStringListConfig = IBinder.FIRST_CALL_TRANSACTION+4;

    /*
     * =================================================
     * INTERFACE DEFINED METHODS
     */

    void putConfig(String name, Object value) throws RemoteException;
    int getIntConfig(String name, int defValue) throws RemoteException;
    String getStringConfig(String name, String defValue) throws RemoteException;
    List<String> getStringListConfig(String name, List<String> defValue) throws RemoteException;

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
                int pos = caller.dataPosition();

                if (caller != null) {
                    caller.writeNoException();
                }

                try {
                    switch (type) {
                        case TRANSACTION_putConfig: {
                            String name = args.readString();
                            Object value = ParcelHelper.unparcelData(args, getClass().getClassLoader());

                            putConfig(name, value);

                        } break; case TRANSACTION_getIntConfig: {
                            String name = args.readString();
                            int defValue = args.readInt();
                            int value = getIntConfig(name, defValue);

                            caller.writeInt(value);

                        } break; case TRANSACTION_getStringConfig: {
                            String name = args.readString();
                            String defValue = (String) args.readValue(String.class.getClassLoader());
                            String value = getStringConfig(name, defValue);

                            caller.writeString(value);

                        } break; case TRANSACTION_getStringListConfig: {
                        /*
                         * We use ParcelHelper in case we have SparseList running through
                         * the default value, which is a proper parcelable class and should not
                         * be written using android's pure Parcel List writer.
                         */
                            String name = args.readString();
                            List<String> defValue = (List<String>) ParcelHelper.unparcelData(args, getClass().getClassLoader());
                            List<String> value = getStringListConfig(name, defValue);

                            ParcelHelper.parcelData(value, caller, 0);

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
                ParcelHelper.parcelData(value, args, 0);
                mBinder.transact(Stub.TRANSACTION_putConfig, args, caller, 0);
                caller.readException();

            } finally {
                args.recycle();
                caller.recycle();
            }
        }

        @Override
        public int getIntConfig(String name, int defValue) throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel caller = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeString(name);
                args.writeInt(defValue);
                mBinder.transact(Stub.TRANSACTION_getIntConfig, args, caller, 0);
                caller.readException();

                return caller.readInt();

            } finally {
                args.recycle();
                caller.recycle();
            }
        }

        @Override
        public String getStringConfig(String name, String defValue) throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel caller = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeString(name);
                args.writeValue(defValue);
                mBinder.transact(Stub.TRANSACTION_getStringConfig, args, caller, 0);
                caller.readException();

                return caller.readString();

            } finally {
                args.recycle();
                caller.recycle();
            }
        }

        @Override
        public List<String> getStringListConfig(String name, List<String> defValue) throws RemoteException {
            Parcel args = Parcel.obtain();
            Parcel caller = Parcel.obtain();

            try {
                args.writeInterfaceToken(DESCRIPTOR);
                args.writeString(name);
                ParcelHelper.parcelData(defValue, args, 0);
                mBinder.transact(Stub.TRANSACTION_getStringListConfig, args, caller, 0);
                caller.readException();

                return (List<String>) ParcelHelper.unparcelData(caller, getClass().getClassLoader());

            } finally {
                args.recycle();
                caller.recycle();
            }
        }
    }
}
