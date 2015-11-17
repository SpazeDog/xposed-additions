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

package com.spazedog.xposed.additionsgb.app.selecter;


import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.v4.util.LruCache;
import android.view.ViewGroup;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.utils.Conversion;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.utils.Constants;

import java.util.Collections;
import java.util.List;

public class AppSingleEntryManager extends UriLauncherEntryManager {

    public AppSingleEntryManager(Selecter selecter, ViewGroup container, int typeFlag) {
        super(selecter, container, typeFlag);
    }

    public class Entry extends UriLauncherEntryManager.Entry {

        protected ApplicationInfo mEntryInfo;

        public Entry(ApplicationInfo info) {
            super(null);

            mEntryInfo = info;
        }

        @Override
        protected String createIconId() {
            return mEntryInfo.uid + "_" + mEntryInfo.icon;
        }

        @Override
        protected Drawable createIcon(PackageManager pm) {
            return mEntryInfo.loadIcon(pm);
        }

        @Override
        public String getLabel() {
            if (mLabel == null) {
                Selecter selecter = getSelecter();
                Context context = selecter.getContext();

                mLabel = (String) mEntryInfo.loadLabel(context.getPackageManager());
            }

            return mLabel;
        }

        @Override
        public String getName() {
            return mEntryInfo.packageName;
        }

        @Override
        public void onClick() {
            HashBundle data = new HashBundle();
            data.put(Selecter.EXTRAS_TYPE, getType());
            data.put(Selecter.EXTRAS_PKG, getName());

            getSelecter().sendMessage(Constants.MSG_DIALOG_SELECTER, data);
        }
    }

    @Override
    public void onLoad() {
        Selecter selecter = getSelecter();
        Context context = selecter.getContext();
        List<ApplicationInfo> appList = context.getPackageManager().getInstalledApplications(0);

        for (ApplicationInfo appInfo : appList) {
            mEntries.add( createEntry(appInfo) );
        }

        Collections.sort(mEntries);
    }

    protected Entry createEntry(ApplicationInfo appInfo) {
        return new Entry(appInfo);
    }
}
