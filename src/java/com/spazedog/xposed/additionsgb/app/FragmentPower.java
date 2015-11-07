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

package com.spazedog.xposed.additionsgb.app;


import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.widget.AppCompatCheckBox;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.ActivityMain.ActivityMainFragment;
import com.spazedog.xposed.additionsgb.app.service.PreferenceServiceMgr;
import com.spazedog.xposed.additionsgb.backend.PowerManager.PowerPlugConfig;
import com.spazedog.xposed.additionsgb.backend.service.BackendService;

public class FragmentPower extends ActivityMainFragment {

    protected ExpandableListView mListView;
    protected PowerAdapter mListAdapter;

    /*
     * =================================================
     * FRAGMENT OVERRIDES
     */

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_power_layout, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mListAdapter = new PowerAdapter();
        mListView = (ExpandableListView) view.findViewById(R.id.power_list);
        mListView.setAdapter(mListAdapter);
        mListView.setOnChildClickListener(mListAdapter);
        mListView.setOnGroupExpandListener(mListAdapter);
    }

    @Override
    public void onPause() {
        super.onPause();

        mListAdapter.saveChanges();
    }

    /*
     * =================================================
     * ADAPTER FOR POWER EXPANDABLE LIST VIEW
     */

    private class PowerAdapter extends BaseExpandableListAdapter implements ExpandableListView.OnChildClickListener, ExpandableListView.OnGroupExpandListener {

        private int mPlugFlags;
        private int mUnplugFlags;

        private boolean mHasChanges = false;

        public PowerAdapter() {
            PreferenceServiceMgr PreferenceMgr = getPreferenceMgr();

            mPlugFlags = PreferenceMgr.getIntConfig("power_plug", PowerPlugConfig.PLUGGED_DEFAULT);
            mUnplugFlags = PreferenceMgr.getIntConfig("power_unplug", PowerPlugConfig.PLUGGED_DEFAULT);
        }

        public void saveChanges() {
            if (mHasChanges) {
                mHasChanges = false;

                PreferenceServiceMgr PreferenceMgr = getPreferenceMgr();
                PreferenceMgr.putConfig("power_plug", mPlugFlags);
                PreferenceMgr.putConfig("power_unplug", mUnplugFlags);

                getBackendMgr().requestServiceReload(BackendService.FLAG_RELOAD_CONFIG);
            }
        }

        @Override
        public int getGroupCount() {
            return 2;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            /*
             * Power Management in Android < JellyBean MR1 does not have
             * separate wireless support. Properly just identified as AC.
             */
            return VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1 ? 4 : 3;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return null;
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return null;
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition + 1;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return (groupPosition + 1) * (childPosition + 1);
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                convertView = inflater.inflate(R.layout.fragment_power_usb_group, parent, false);
            }

            TextView groupTitle = (TextView) convertView.findViewById(R.id.power_list_title_group);
            TextView groupSummary = (TextView) convertView.findViewById(R.id.power_list_summary_group);

            switch (groupPosition) {
                case 0:
                    groupTitle.setText(R.string.power_name_plug);
                    groupSummary.setText(R.string.power_summary_plug);

                    break;

                case 1:
                    groupTitle.setText(R.string.power_name_unplug);
                    groupSummary.setText(R.string.power_summary_unplug);
            }

            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                convertView = inflater.inflate(R.layout.fragment_power_usb_item, parent, false);
            }

            int flags = groupPosition == 0 ? mPlugFlags : mUnplugFlags;
            TextView itemTitle = (TextView) convertView.findViewById(R.id.power_list_title_item);
            AppCompatCheckBox itemCheckbox = (AppCompatCheckBox) convertView.findViewById(R.id.power_list_checkbox_item);

            switch (childPosition) {
                case 0:
                    itemTitle.setText("- " + getResources().getString(R.string.power_plugtype_default));
                    itemTitle.setEnabled(true);
                    itemCheckbox.setChecked(flags == PowerPlugConfig.PLUGGED_DEFAULT);
                    itemCheckbox.setEnabled(true);

                    break;

                case 1:
                    itemTitle.setText("- " + getResources().getString(R.string.power_plugtype_usb));
                    itemTitle.setEnabled(flags != PowerPlugConfig.PLUGGED_DEFAULT);
                    itemCheckbox.setChecked((flags & PowerPlugConfig.PLUGGED_USB) != 0 && itemTitle.isEnabled());
                    itemCheckbox.setEnabled(itemTitle.isEnabled());

                    break;

                case 2:
                    itemTitle.setText("- " + getResources().getString(R.string.power_plugtype_ac));
                    itemTitle.setEnabled(flags != PowerPlugConfig.PLUGGED_DEFAULT);
                    itemCheckbox.setChecked((flags & PowerPlugConfig.PLUGGED_AC) != 0 && itemTitle.isEnabled());
                    itemCheckbox.setEnabled(itemTitle.isEnabled());

                    break;

                case 3:
                    itemTitle.setText("- " + getResources().getString(R.string.power_plugtype_wl));
                    itemTitle.setEnabled(flags != PowerPlugConfig.PLUGGED_DEFAULT);
                    itemCheckbox.setChecked((flags & PowerPlugConfig.PLUGGED_WIRELESS) != 0 && itemTitle.isEnabled());
                    itemCheckbox.setEnabled(itemTitle.isEnabled());
            }

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            int flags = groupPosition == 0 ? mPlugFlags : mUnplugFlags;

            return childPosition == 0 || flags != -1;
        }

        @Override
        public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
            int flags = groupPosition == 0 ? mPlugFlags : mUnplugFlags;

            switch (childPosition) {
                case 0:
                    flags = flags == PowerPlugConfig.PLUGGED_DEFAULT ? 0 : PowerPlugConfig.PLUGGED_DEFAULT; break;

                case 1:
                    flags = (flags & PowerPlugConfig.PLUGGED_USB) != 0 ? flags & ~PowerPlugConfig.PLUGGED_USB : flags | PowerPlugConfig.PLUGGED_USB; break;

                case 2:
                    flags = (flags & PowerPlugConfig.PLUGGED_AC) != 0 ? flags & ~PowerPlugConfig.PLUGGED_AC : flags | PowerPlugConfig.PLUGGED_AC; break;

                case 3:
                    flags = (flags & PowerPlugConfig.PLUGGED_WIRELESS) != PowerPlugConfig.PLUGGED_WIRELESS ? flags & ~PowerPlugConfig.PLUGGED_WIRELESS : flags | PowerPlugConfig.PLUGGED_WIRELESS;
            }

            if (groupPosition == 0) {
                mPlugFlags = flags;

            } else {
                mUnplugFlags = flags;
            }

            notifyDataSetChanged();

            return (mHasChanges = true);
        }

        @Override
        public void onGroupExpand(int groupPosition) {
            for (int i=0; i < getGroupCount(); i++) {
                if (i != groupPosition) {
                    mListView.collapseGroup(i);
                }
            }
        }
    }
}
