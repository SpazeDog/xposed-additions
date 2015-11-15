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


import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.AppCompatCheckBox;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import com.spazedog.lib.utilsLib.app.widget.ExpandableView;
import com.spazedog.lib.utilsLib.app.widget.ExpandableView.ExpandableAdapter;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.ActivityMain.ActivityMainFragment;
import com.spazedog.xposed.additionsgb.app.service.PreferenceServiceMgr;
import com.spazedog.xposed.additionsgb.backend.service.BackendService;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Type;

public class FragmentSettings extends ActivityMainFragment implements OnClickListener {

    protected ExpandableView mListView;
    protected DebugAdapter mListAdapter;

    private boolean mHasChanges = false;
    private boolean mOwnerLock = false;

    private View mOwnerlockWrapper;
    private CheckBox mOwnerlockCheckbox;


    /*
     * =================================================
     * FRAGMENT OVERRIDES
     */

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings_layout, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PreferenceServiceMgr preferenceMgr = getPreferenceMgr();

        if (Utils.isOwner(getActivity())) {
            mListAdapter = new DebugAdapter();
            mListView = (ExpandableView) view.findViewById(R.id.settings_debug_list);
            mListView.setAdapter(mListAdapter);

            mOwnerLock = preferenceMgr.getIntConfig("owner_lock") > 0;
            mOwnerlockWrapper = view.findViewById(R.id.settings_wrapper_ownerlock);

            /*
             * No multi users before Jellybean 4.2
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mOwnerlockWrapper.setOnClickListener(this);
                mOwnerlockCheckbox = (CheckBox) mOwnerlockWrapper.findViewById(R.id.preference_widget);
                mOwnerlockCheckbox.setChecked(mOwnerLock);

                TextView lockTitle = (TextView) mOwnerlockWrapper.findViewById(R.id.preference_title);
                lockTitle.setText(R.string.settings_name_owner_lock);

                TextView lockSummary = (TextView) mOwnerlockWrapper.findViewById(R.id.preference_summary);
                lockSummary.setText(R.string.settings_summary_owner_lock);

            } else {
                mOwnerlockWrapper.setVisibility(View.GONE);
            }

        } else {
            view.findViewById(R.id.settings_owner_group).setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (Utils.isOwner(getActivity())) {
            PreferenceServiceMgr preferenceMgr = getPreferenceMgr();

            if (mHasChanges) {
                preferenceMgr.putConfig("owner_lock", mOwnerLock ? 1 : 0);
            }

            if (mListAdapter.saveChanges() || mHasChanges) {
                BackendServiceMgr backendMgr = getBackendMgr();

                if (backendMgr != null) {
                    getBackendMgr().requestServiceReload(BackendService.FLAG_RELOAD_CONFIG);
                }
            }
        }

        mHasChanges = false;
    }


    /*
     * =================================================
     * INTERFACES OVERRIDES
     */

    @Override
    public void onClick(View v) {
        mHasChanges = true;

        if (v == mOwnerlockCheckbox) {
            mOwnerlockCheckbox.setChecked( (mOwnerLock = !mOwnerLock) );
        }
    }


    /*
     * =================================================
     * ADAPTER FOR DEBUG EXPANDABLE LIST VIEW
     */

    private class DebugAdapter extends ExpandableAdapter {

        private boolean mHasChanges = false;
        private int mFlags = 0;

        public DebugAdapter() {
            mFlags = getPreferenceMgr().getIntConfig("debug_flags", Type.DISABLED);
        }

        public boolean saveChanges() {
            if (mHasChanges) {
                getPreferenceMgr().putConfig("debug_flags", mFlags); return true;
            }

            mHasChanges = false;

            return false;
        }

        @Override
        public int getGroupCount() {
            return 1;
        }

        @Override
        public int getChildCount(int groupPosition) {
            return 7;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                convertView = inflater.inflate(R.layout.expandable_list_group, parent, false);
            }

            TextView groupTitle = (TextView) convertView.findViewById(R.id.expandable_list_title_group);
            TextView groupSummary = (TextView) convertView.findViewById(R.id.expandable_list_summary_group);

            groupTitle.setText(R.string.settings_name_owner_debug);
            groupSummary.setText(R.string.settings_summary_owner_debug);

            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                convertView = inflater.inflate(R.layout.expandable_list_item_checkbox, parent, false);
            }

            TextView itemTitle = (TextView) convertView.findViewById(R.id.expandable_list_title_item);
            AppCompatCheckBox itemCheckbox = (AppCompatCheckBox) convertView.findViewById(R.id.expandable_list_checkbox_item);

            switch (childPosition) {
                case 0:
                    itemTitle.setText(R.string.settings_debugtype_enable);
                    itemTitle.setEnabled(true);
                    itemCheckbox.setChecked(mFlags != Type.DISABLED);
                    itemCheckbox.setEnabled(true);

                    break;

                case 1:
                    itemTitle.setText(R.string.settings_debugtype_extended);
                    itemTitle.setEnabled(mFlags != Type.DISABLED);
                    itemCheckbox.setChecked((mFlags & Type.EXTENDED) != 0 && itemTitle.isEnabled());
                    itemCheckbox.setEnabled(itemTitle.isEnabled());

                    break;

                case 2:
                    itemTitle.setText(R.string.settings_debugtype_buttons);
                    itemTitle.setEnabled(mFlags != Type.DISABLED);
                    itemCheckbox.setChecked((mFlags & Type.BUTTONS) != 0 && itemTitle.isEnabled());
                    itemCheckbox.setEnabled(itemTitle.isEnabled());

                    break;

                case 3:
                    itemTitle.setText(R.string.settings_debugtype_power);
                    itemTitle.setEnabled(mFlags != Type.DISABLED);
                    itemCheckbox.setChecked((mFlags & Type.POWER) != 0 && itemTitle.isEnabled());
                    itemCheckbox.setEnabled(itemTitle.isEnabled());

                    break;

                case 4:
                    itemTitle.setText(R.string.settings_debugtype_layout);
                    itemTitle.setEnabled(mFlags != Type.DISABLED);
                    itemCheckbox.setChecked((mFlags & Type.LAYOUT) != 0 && itemTitle.isEnabled());
                    itemCheckbox.setEnabled(itemTitle.isEnabled());

                    break;

                case 5:
                    itemTitle.setText(R.string.settings_debugtype_state);
                    itemTitle.setEnabled(mFlags != Type.DISABLED);
                    itemCheckbox.setChecked((mFlags & Type.STATE) != 0 && itemTitle.isEnabled());
                    itemCheckbox.setEnabled(itemTitle.isEnabled());

                    break;

                case 6:
                    itemTitle.setText(R.string.settings_debugtype_services);
                    itemTitle.setEnabled(mFlags != Type.DISABLED);
                    itemCheckbox.setChecked((mFlags & Type.SERVICES) != 0 && itemTitle.isEnabled());
                    itemCheckbox.setEnabled(itemTitle.isEnabled());
            }

            return convertView;
        }

        @Override
        public boolean isChildClickable(int groupPosition, int childPosition, View view) {
            return childPosition == 0 || mFlags != Type.DISABLED;
        }

        @Override
        public boolean onChildClick(int groupPosition, int childPosition, View v) {
            switch (childPosition) {
                case 0:
                    mFlags = mFlags == Type.DISABLED ? 0 : Type.DISABLED; break;

                case 1:
                    mFlags = (mFlags & Type.EXTENDED) != 0 ? mFlags & ~Type.EXTENDED : mFlags | Type.EXTENDED; break;

                case 2:
                    mFlags = (mFlags & Type.BUTTONS) != 0 ? mFlags & ~Type.BUTTONS : mFlags | Type.BUTTONS; break;

                case 3:
                    mFlags = (mFlags & Type.POWER) != 0 ? mFlags & ~Type.POWER : mFlags | Type.POWER; break;

                case 4:
                    mFlags = (mFlags & Type.LAYOUT) != 0 ? mFlags & ~Type.LAYOUT : mFlags | Type.LAYOUT; break;

                case 5:
                    mFlags = (mFlags & Type.STATE) != 0 ? mFlags & ~Type.STATE : mFlags | Type.STATE; break;

                case 6:
                    mFlags = (mFlags & Type.SERVICES) != 0 ? mFlags & ~Type.SERVICES : mFlags | Type.SERVICES;
            }

            notifyDataSetChanged();

            return (mHasChanges = true);
        }
    }
}
