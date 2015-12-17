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
import android.widget.CheckBox;
import android.widget.TextView;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.app.widget.ExpandableView;
import com.spazedog.lib.utilsLib.app.widget.ExpandableView.ExpandableAdapter;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.ActivityMain.ActivityMainFragment;
import com.spazedog.xposed.additionsgb.app.selecter.Selecter;
import com.spazedog.xposed.additionsgb.app.service.PreferenceServiceMgr;
import com.spazedog.xposed.additionsgb.backend.PowerManager.PowerPlugConfig;
import com.spazedog.xposed.additionsgb.backend.service.BackendService;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.utils.Constants;

import java.util.List;

public class FragmentDisplay extends ActivityMainFragment implements OnClickListener {

    protected ExpandableView mListView;
    protected PowerAdapter mListAdapter;

    private View mLaunchWrapper;
    private View mRotationBlacklistWrapper;
    private View mRotationWrapper;
    private CheckBox mRotationCheckbox;

    private boolean mHasChanges = false;
    private boolean mOverwriteRotation = false;
    private List<String> mRotationBlacklist;
    private List<String> mLaunchSelection;

    private static final int REQUEST_ROTATION = 1;
    private static final int REQUEST_LAUNCH = 2;


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
        mLaunchSelection = PreferenceMgr.getStringListConfig("launch_selection", null);


        /*
         * Setup Power Expandable list
         */

        mListAdapter = new PowerAdapter();
        mListView = (ExpandableView) view.findViewById(R.id.display_usb_list);
        mListView.setAdapter(mListAdapter);


        /*
         * Setup Blacklist Button
         */

        mRotationBlacklistWrapper = view.findViewById(R.id.display_wrapper_rotation_blacklist);
        mRotationBlacklistWrapper.setOnClickListener(this);

        TextView blacklistTitle = (TextView) mRotationBlacklistWrapper.findViewById(R.id.preference_title);
        blacklistTitle.setText(R.string.display_name_rotation_blacklist);

        TextView blacklistSummary = (TextView) mRotationBlacklistWrapper.findViewById(R.id.preference_summary);
        blacklistSummary.setText(R.string.display_summary_rotation_blacklist);


        /*
         * Setup Rotation checkbox
         */

        mRotationWrapper = view.findViewById(R.id.display_wrapper_rotation);
        mRotationWrapper.setOnClickListener(this);
        mRotationCheckbox = (CheckBox) mRotationWrapper.findViewById(R.id.preference_widget);
        mRotationCheckbox.setChecked(mOverwriteRotation);

        TextView rotationTitle = (TextView) mRotationWrapper.findViewById(R.id.preference_title);
        rotationTitle.setText(R.string.display_name_rotation);

        TextView rotationSummary = (TextView) mRotationWrapper.findViewById(R.id.preference_summary);
        rotationSummary.setText(R.string.display_summary_rotation);


        /*
         * Setup Launch Selection
         */

        mLaunchWrapper = view.findViewById(R.id.display_wrapper_launch_selection);
        mLaunchWrapper.setOnClickListener(this);

        TextView launchTitle = (TextView) mLaunchWrapper.findViewById(R.id.preference_title);
        launchTitle.setText(R.string.display_name_launch_selection);

        TextView launchSummary = (TextView) mLaunchWrapper.findViewById(R.id.preference_summary);
        launchSummary.setText(R.string.display_summary_launch_selection);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mHasChanges) {
            PreferenceServiceMgr PreferenceMgr = getPreferenceMgr();
            PreferenceMgr.putConfig("rotation_overwrite", mOverwriteRotation ? 1 : 0);
            PreferenceMgr.putConfig("rotation_blacklist", mRotationBlacklist);
            PreferenceMgr.putConfig("launch_selection", mLaunchSelection);
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
            case Constants.MSG_DIALOG_SELECTER:
                int selectorType = data.getInt(Selecter.EXTRAS_TYPE);

                if (selectorType == FragmentSelecter.APP_MULTI) {
                    int request = data.getInt(Selecter.EXTRAS_RESPONSE);

                    if (request == REQUEST_LAUNCH) {
                        mLaunchSelection = data.getStringList(Selecter.EXTRAS_PKGS);

                    } else {
                        mRotationBlacklist = data.getStringList(Selecter.EXTRAS_PKGS);
                    }

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
         * No changes are made unless it returns something.
         * The same with mLaunchWrapper.
         */
        mHasChanges = v != mRotationBlacklistWrapper && v != mLaunchWrapper;

        if (v == mRotationWrapper) {
            mRotationCheckbox.setChecked( (mOverwriteRotation = !mOverwriteRotation) );

        } else {
            HashBundle args = new HashBundle();
            args.putInt(Selecter.ARGS_FLAGS, FragmentSelecter.APP_MULTI);

            if (v == mLaunchWrapper) {
                args.putInt(Selecter.ARGS_REQUEST, REQUEST_LAUNCH);
                args.putStringList(Selecter.ARGS_SELECTED, mLaunchSelection);

            } else {
                args.putInt(Selecter.ARGS_REQUEST, REQUEST_ROTATION);
                args.putStringList(Selecter.ARGS_SELECTED, mRotationBlacklist);
            }

            loadFragment(R.id.fragment_selecter, args, false);
        }
    }


    /*
     * =================================================
     * ADAPTER FOR POWER EXPANDABLE LIST VIEW
     */

    private class PowerAdapter extends ExpandableAdapter {

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
        public int getChildCount(int groupPosition) {
            /*
             * Power Management in Android < JellyBean MR1 does not have
             * separate wireless support. Properly just identified as AC.
             */
            return VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1 ? 4 : 3;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                convertView = inflater.inflate(R.layout.expandable_list_group, parent, false);
            }

            TextView groupTitle = (TextView) convertView.findViewById(R.id.expandable_list_title_group);
            TextView groupSummary = (TextView) convertView.findViewById(R.id.expandable_list_summary_group);

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
        public View getChildView(int groupPosition, int childPosition, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                convertView = inflater.inflate(R.layout.expandable_list_item_checkbox, parent, false);
            }

            int flags = groupPosition == 0 ? mPlugFlags : mUnplugFlags;
            TextView itemTitle = (TextView) convertView.findViewById(R.id.expandable_list_title_item);
            AppCompatCheckBox itemCheckbox = (AppCompatCheckBox) convertView.findViewById(R.id.expandable_list_checkbox_item);

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
        public boolean isChildClickable(int groupPosition, int childPosition, View view) {
            int flags = groupPosition == 0 ? mPlugFlags : mUnplugFlags;

            return childPosition == 0 || flags != -1;
        }

        @Override
        public boolean onChildClick(int groupPosition, int childPosition, View v) {
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
    }
}
