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


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Parcel;

import com.spazedog.lib.utilsLib.MultiParcelableBuilder;

public class PreferenceDatabase extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "preferences";
    public static final int DATABASE_VERSION = 1;

    public static abstract class DatabaseEntry extends MultiParcelableBuilder {
        public static final String TABLE_NAME = null;
        public static final String COLUMN_ID = "cId";

        public final int id;

        private DatabaseEntry(Parcel in, ClassLoader loader) {
            super(in, loader);

            id = (Integer) unparcelData(in, loader);
        }

        public DatabaseEntry(int id) {
            this.id = id;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);

            parcelData(id, out, flags);
        }
    }

    public static class SettingsEntry<T> extends DatabaseEntry {
        public static final String TABLE_NAME = "settings";
        public static final String COLUMN_NAME = "cName";
        public static final String COLUMN_VALUE = "cValue";
        public static final String COLUMN_TYPE = "cType";

        public static final int TYPE_NULL = 0;
        public static final int TYPE_INTEGER = 1;
        public static final int TYPE_STRING = 2;

        public final int type;
        public final T value;
        public final String name;

        private SettingsEntry(Parcel in, ClassLoader loader) {
            super(in, loader);

            this.type = (Integer) unparcelData(in, loader);
            this.value = (T) unparcelData(in, loader);
            this.name = (String) unparcelData(in, loader);
        }

        public SettingsEntry(int id, String name, T value, int type) {
            super(id);

            this.type = type;
            this.value = value;
            this.name = name;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);

            parcelData(type, out, flags);
            parcelData(value, out, flags);
            parcelData(name, out, flags);
        }
    }

    public PreferenceDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + SettingsEntry.TABLE_NAME + " (" +
                        SettingsEntry.COLUMN_ID + " INTEGER PRIMARY KEY," +
                        SettingsEntry.COLUMN_NAME + " TEXT UNIQUE," +
                        SettingsEntry.COLUMN_VALUE + " TEXT," +
                        SettingsEntry.COLUMN_TYPE + " INTEGER" +
                ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
