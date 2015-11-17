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


import android.content.pm.ApplicationInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.utils.Constants;

import java.util.List;

public class AppMultiEntryManager extends AppSingleEntryManager {

    protected List<String> mSelected;
    protected boolean mHasChanges = false;

    public AppMultiEntryManager(Selecter selecter, ViewGroup container, int typeFlag) {
        super(selecter, container, typeFlag);

        HashBundle data = selecter.getArgs();

        if (data.containsKey(Selecter.ARGS_SELECTED)) {
            mSelected = data.getStringList(Selecter.ARGS_SELECTED);
        }
    }

    public class Adapter extends AppSingleEntryManager.Adapter {

        public class ViewHolder extends AppSingleEntryManager.Adapter.ViewHolder {

            public final CheckBox CHECKBOX;

            public ViewHolder(ViewGroup view) {
                super(view);

                CHECKBOX = (CheckBox) view.findViewById(R.id.item_checkbox);
                CHECKBOX.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected ViewHolder createViewHolder(ViewGroup view) {
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(AppSingleEntryManager.Adapter.ViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);

            Entry entry = (Entry) getManager().getEntry(position);
            ((ViewHolder) holder).CHECKBOX.setChecked(entry.isSelected());
        }

        @Override
        public void onClick(View v) {
            super.onClick(v);

            int position = (Integer) v.getTag(R.id.tag_holder);
            Entry entry = (Entry) getManager().getEntry(position);

            if (entry != null) {
                ((CheckBox) v.findViewById(R.id.item_checkbox)).setChecked(entry.isSelected());
            }
        }
    }

    public class Entry extends AppSingleEntryManager.Entry {

        protected boolean mIsSelected = false;

        public Entry(ApplicationInfo info) {
            super(info);

            if (mSelected != null) {
                mIsSelected = mSelected.contains(mEntryInfo.packageName);
            }
        }

        public boolean isSelected() {
            return mIsSelected;
        }

        @Override
        public void onClick() {
            mHasChanges = true;
            mIsSelected = !mIsSelected;
        }
    }

    @Override
    public void onDestroy() {
        if (mHasChanges) {
            List<String> pkgs = new SparseList<String>();

            for (UriLauncherEntryManager.Entry entry : mEntries) {
                if (((Entry) entry).isSelected()) {
                    pkgs.add(entry.getName());
                }
            }

            HashBundle data = new HashBundle();
            data.put(Selecter.EXTRAS_TYPE, getType());
            data.putStringList(Selecter.EXTRAS_PKGS, pkgs);

            getSelecter().sendMessage(Constants.MSG_DIALOG_SELECTER, data);
        }

        super.onDestroy();
    }

    @Override
    public AppSingleEntryManager.Adapter getAdaptor() {
        if (mAdapter == null) {
            mAdapter = new Adapter();
        }

        return mAdapter;
    }

    @Override
    protected Entry createEntry(ApplicationInfo appInfo) {
        return new Entry(appInfo);
    }
}
