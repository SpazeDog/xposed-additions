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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.os.Process;
import android.text.TextUtils;

import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.xposed.additionsgb.app.service.PreferenceDatabase.SettingsEntry;
import com.spazedog.xposed.additionsgb.utils.Constants;

import java.util.List;
import java.util.Objects;

public class PreferenceService extends Service {
	public static final String TAG = PreferenceService.class.getName();

    protected static Proxy oProxy;
    protected static final Object mLock = new Object();

    public static PreferenceProxy bind(Context context) {
        synchronized (mLock) {
            if (oProxy == null) {
                oProxy = new Proxy(context);
            }

            oProxy.REFS++;

            return oProxy;
        }
    }

    public static void unbind(PreferenceProxy preferenceProxy) {
        synchronized (mLock) {
            if (preferenceProxy instanceof Proxy) {
                Proxy proxy = (Proxy) preferenceProxy;

                proxy.REFS--;

                if (proxy.REFS <= 0) {
                    proxy.close();

                    if (proxy == oProxy) {
                        oProxy = null;
                    }
                }
            }
        }
    }

    protected Proxy mProxy;

    @Override
    public void onCreate() {
        super.onCreate();

        mProxy = (Proxy) bind(getApplicationContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unbind(mProxy);

        mProxy = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mProxy;
    }

    public static class Proxy extends PreferenceProxy.Stub {

        protected int REFS = 0;

        protected PreferenceDatabase mDatabase;
        protected Context mContext;

        protected Proxy(Context context) {
            mContext = context.getApplicationContext();
            mDatabase = new PreferenceDatabase(mContext);
        }

        protected void close() {
            mDatabase.close();
            mDatabase = null;
            mContext = null;
        }

        protected boolean checkPermission(String permission) {
            int uid = getCallingUid();

            return uid == Process.myUid() ||
                        uid == Process.SYSTEM_UID ||
                            mContext.checkCallingPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }

        protected Object getConfig(String name, Object defValue) {
            if (!checkPermission(Constants.PERMISSION_SETTINGS_RW)) {
                throw new SecurityException("Application has not acquired the permission '" + Constants.PERMISSION_SETTINGS_RW + "");
            }

            int type = -1;
            Object value = defValue;
            SQLiteDatabase db = mDatabase.getReadableDatabase();
            Cursor result = db.rawQuery(String.format("SELECT %s, %s FROM %s WHERE %s = '%s'", SettingsEntry.COLUMN_TYPE, SettingsEntry.COLUMN_VALUE, SettingsEntry.TABLE_NAME, SettingsEntry.COLUMN_NAME, name), null);

            if (result.moveToNext()) {
                type = result.getInt(0);
                value = result.getString(1);
            }

            result.close();
            db.close();

            if (value != null && type != -1) {
                switch (type) {
                    case SettingsEntry.TYPE_INTEGER:
                        value = Integer.valueOf((String) value); break;

                    case SettingsEntry.TYPE_NULL:
                        value = null; break;

                    case SettingsEntry.TYPE_LIST:
                        String[] list = ((String) value).split(",");
                        value = new SparseList<String>();

                        if (!"".equals(value)) {
                            for (String item : list) {
                                ((List) value).add(item);
                            }
                        }
                }
            }

            return value;
        }

        @Override
        public void putConfig(String name, Object value) {
            if (!checkPermission(Constants.PERMISSION_SETTINGS_RW)) {
                throw new SecurityException("Application has not acquired the permission '" + Constants.PERMISSION_SETTINGS_RW + "");
            }

            int type = 0;

            if (value == null) {
                type = SettingsEntry.TYPE_NULL;

            } else if (value instanceof Integer) {
                type = SettingsEntry.TYPE_INTEGER;
                value = String.valueOf(value);

            } else if (value instanceof String) {
                type = SettingsEntry.TYPE_STRING;

            } else if (value instanceof List) {
                type = SettingsEntry.TYPE_LIST;
                value = TextUtils.join(",", (List) value);

            } else {
                return;
            }

            SQLiteDatabase db = mDatabase.getWritableDatabase();
            db.execSQL(String.format("INSERT OR REPLACE INTO %s (%s, %s, %s) VALUES ('%s', '%s', %d)", SettingsEntry.TABLE_NAME, SettingsEntry.COLUMN_NAME, SettingsEntry.COLUMN_VALUE, SettingsEntry.COLUMN_TYPE, name, (value == null ? "" : (String) value), type));
            db.close();
        }

        @Override
        public int getIntConfig(String name, int defValue) {
            Object val = getConfig(name, defValue);

            return val != null ? (Integer) val : defValue;
        }

        @Override
        public String getStringConfig(String name, String defValue) {
            return (String) getConfig(name, defValue);
        }

        @Override
        public List<String> getStringListConfig(String name, List<String> defValue) {
            return (List<String>) getConfig(name, defValue);
        }
	};
}
