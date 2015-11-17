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


import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.view.ViewGroup;

import com.spazedog.xposed.additionsgb.R;

public class UriActivityEntryManager extends UriLauncherEntryManager {

    public UriActivityEntryManager(Selecter selecter, ViewGroup container, int typeFlag) {
        super(selecter, container, typeFlag);
    }

    public class Entry extends UriLauncherEntryManager.Entry {

        public Entry(ActivityInfo info) {
            super(info);
        }

        @Override
        public String getName() {
            return mEntryInfo.name;
        }
    }

    @Override
    protected Entry createEntry(ActivityInfo activityInfo) {
        return new Entry(activityInfo);
    }

    @Override
    protected Intent getLoadIntent() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);

        return intent;
    }

    @Override
    protected int getLabelResId() {
        return R.string.selecter_page_title_activity;
    }
}
