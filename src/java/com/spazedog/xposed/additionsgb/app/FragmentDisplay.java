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
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.TextView;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.ActivityMain.ActivityMainFragment;
import com.spazedog.xposed.additionsgb.app.service.PreferenceServiceMgr;
import com.spazedog.xposed.additionsgb.backend.PowerManager.PowerPlugConfig;
import com.spazedog.xposed.additionsgb.backend.service.BackendService;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class FragmentDisplay extends ActivityMainFragment implements OnClickListener {

    protected ExpandableListView mListView;
    protected PowerAdapter mListAdapter;

    private View mRotationBlacklistWrapper;
    private View mRotationWrapper;
    private CheckBox mRotationCheckbox;

    private boolean mHasChanges = false;
    private boolean mOverwriteRotation = false;
    private List<String> mRotationBlacklist;


    /*
     * =================================================
     * FRAGMENT OVERRIDES
     */

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_display_layout, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PreferenceServiceMgr PreferenceMgr = getPreferenceMgr();

        mOverwriteRotation = PreferenceMgr.getIntConfig("rotation_overwrite", 0) > 0;
        mRotationBlacklist = PreferenceMgr.getStringListConfig("rotation_blacklist", null);

        mListAdapter = new PowerAdapter();
        mListView = (ExpandableListView) view.findViewById(R.id.display_usb_list);
        mListView.setAdapter(mListAdapter);
        mListView.setOnChildClickListener(mListAdapter);
        mListView.setOnGroupExpandListener(mListAdapter);

        mRotationBlacklistWrapper = view.findViewById(R.id.display_wrapper_rotation_blacklist);
        mRotationBlacklistWrapper.setOnClickListener(this);

        mRotationWrapper = view.findViewById(R.id.display_wrapper_rotation);
        mRotationWrapper.setOnClickListener(this);
        mRotationCheckbox = (CheckBox) view.findViewById(R.id.display_checkbox_rotation);
        mRotationCheckbox.setChecked(mOverwriteRotation);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mHasChanges) {
            PreferenceServiceMgr PreferenceMgr = getPreferenceMgr();
            PreferenceMgr.putConfig("rotation_overwrite", mOverwriteRotation ? 1 : 0);
            PreferenceMgr.putConfig("rotation_blacklist", mRotationBlacklist);
        }

        if (mListAdapter.saveChanges() || mHasChanges) {
            BackendServiceMgr backendMgr = getBackendMgr();

            if (backendMgr != null) {
                getBackendMgr().requestServiceReload(BackendService.FLAG_RELOAD_CONFIG);
            }
        }

        mHasChanges = false;
    }

    @Override
    public void onReceiveMessage(int type, HashBundle data, boolean isSticky) {
        switch (type) {
            case Constants.MSG_DIALOG_SELECTOR:
                int selectorType = data.getInt(FragmentLaunchSelector.EXTRAS_TYPE);

                if (selectorType == FragmentLaunchSelector.APP_MULTI) {
                    mRotationBlacklist = data.getStringList(FragmentLaunchSelector.EXTRAS_PKGS);
                    mHasChanges = true;
                }
        }
    }

    /*
     * =================================================
     * INTERFACES OVERRIDES
     */

    @Override
    public void onClick(View v) {
        /*
         * mRotationBlacklistWrapper is just a dialog request.
         * No changes are made unless it returns something
         */
        mHasChanges = v != mRotationBlacklistWrapper;

        if (v == mRotationWrapper) {
            mRotationCheckbox.setChecked( (mOverwriteRotation = !mOverwriteRotation) );

        } else {
            HashBundle args = new HashBundle();
            args.putInt(FragmentLaunchSelector.ARGS_FLAGS, FragmentLaunchSelector.APP_MULTI);
            args.putStringList(FragmentLaunchSelector.ARGS_SELECTED, mRotationBlacklist);

            loadFragment(R.id.fragment_launch_selector, args, false);
        }
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

        public boolean saveChanges() {
            if (mHasChanges) {
                mHasChanges = false;

                PreferenceServiceMgr PreferenceMgr = getPreferenceMgr();
                PreferenceMgr.putConfig("power_plug", mPlugFlags);
                PreferenceMgr.putConfig("power_unplug", mUnplugFlags);

                return true;
            }

            return false;
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
                convertView = inflater.inflate(R.layout.fragment_display_usb_group, parent, false);
            }

            TextView groupTitle = (TextView) convertView.findViewById(R.id.display_usb_list_title_group);
            TextView groupSummary = (TextView) convertView.findViewById(R.id.display_usb_list_summary_group);

            switch (groupPosition) {
                case 0:
                    groupTitle.setText(R.string.display_name_plug);
                    groupSummary.setText(R.string.display_summary_plug);

                    break;

                case 1:
                    groupTitle.setText(R.string.display_name_unplug);
                    groupSummary.setText(R.string.display_summary_unplug);
            }

            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                convertView = inflater.inflate(R.layout.fragment_display_usb_item, parent, false);
            }

            int flags = groupPosition == 0 ? mPlugFlags : mUnplugFlags;
            TextView itemTitle = (TextView) convertView.findViewById(R.id.display_usb_list_title_item);
            AppCompatCheckBox itemCheckbox = (AppCompatCheckBox) convertView.findViewById(R.id.display_usb_list_checkbox_item);

            switch (childPosition) {
                case 0:
                    itemTitle.setText(R.string.display_plugtype_default);
                    itemTitle.setEnabled(true);
                    itemCheckbox.setChecked(flags == PowerPlugConfig.PLUGGED_DEFAULT);
                    itemCheckbox.setEnabled(true);

                    break;

                case 1:
                    itemTitle.setText(R.string.display_plugtype_usb);
                    itemTitle.setEnabled(flags != PowerPlugConfig.PLUGGED_DEFAULT);
                    itemCheckbox.setChecked((flags & PowerPlugConfig.PLUGGED_USB) != 0 && itemTitle.isEnabled());
                    itemCheckbox.setEnabled(itemTitle.isEnabled());

                    break;

                case 2:
                    itemTitle.setText(R.string.display_plugtype_ac);
                    itemTitle.setEnabled(flags != PowerPlugConfig.PLUGGED_DEFAULT);
                    itemCheckbox.setChecked((flags & PowerPlugConfig.PLUGGED_AC) != 0 && itemTitle.isEnabled());
                    itemCheckbox.setEnabled(itemTitle.isEnabled());

                    break;

                case 3:
                    itemTitle.setText(R.string.display_plugtype_wl);
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
