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
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.ActivityMain.ActivityMainFragment;
import com.spazedog.xposed.additionsgb.app.service.PreferenceServiceMgr;
import com.spazedog.xposed.additionsgb.backend.service.BackendService;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.utils.Utils;

public class FragmentSettings extends ActivityMainFragment implements OnClickListener {

    private boolean mHasChanges = false;
    private boolean mEnabledebug = false;
    private boolean mOwnerLock = false;

    private View mDebugWrapper;
    private CheckBox mDebugCheckbox;

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

        if (!Utils.isOwner(getActivity())) {
            mEnabledebug = preferenceMgr.getIntConfig("enable_debug") > 0;
            mOwnerLock = preferenceMgr.getIntConfig("owner_lock") > 0;

            mDebugWrapper = view.findViewById(R.id.settings_wrapper_debug);
            mDebugWrapper.setOnClickListener(this);
            mDebugCheckbox = (CheckBox) mDebugWrapper.findViewById(R.id.settings_checkbox_debug);
            mDebugCheckbox.setChecked(mEnabledebug);

            mOwnerlockWrapper = view.findViewById(R.id.settings_wrapper_ownerlock);

            /*
             * No multi users before Jellybean 4.2
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mOwnerlockWrapper.setOnClickListener(this);
                mOwnerlockCheckbox = (CheckBox) mOwnerlockWrapper.findViewById(R.id.settings_checkbox_ownerlock);
                mOwnerlockCheckbox.setChecked(mOwnerLock);

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

        if (mHasChanges) {
            mHasChanges = false;

            BackendServiceMgr backendMgr = getBackendMgr();
            PreferenceServiceMgr preferenceMgr = getPreferenceMgr();

            if (Utils.isOwner(getActivity())) {
                preferenceMgr.putConfig("enable_debug", mEnabledebug ? 1 : 0);
                preferenceMgr.putConfig("owner_lock", mOwnerLock ? 1 : 0);
            }

            if (backendMgr != null) {
                backendMgr.requestServiceReload(BackendService.FLAG_RELOAD_CONFIG);
            }
        }
    }

    /*
     * =================================================
     * INTERFACES OVERRIDES
     */

    @Override
    public void onClick(View v) {
        mHasChanges = true;

        if (v == mDebugWrapper) {
            mDebugCheckbox.setChecked( (mEnabledebug = !mEnabledebug) );

        } else {
            mOwnerlockCheckbox.setChecked( (mOwnerLock = !mOwnerLock) );
        }
    }
}
