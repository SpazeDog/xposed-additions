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


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.ActivityMain.ActivityMainDialog;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr.ServiceListener;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.InputDeviceInfo;

public class FragmentKeyReceiver extends ActivityMainDialog implements ServiceListener, OnClickListener {

    private TextView mSummaryView;
    private TextView mTypeResultView;
    private TextView mFlagsResultView;
    private View mPosBtn;
    private View mNegBtn;

    private int mKeyCode = 0;
    private int mDeviceFlags = 0;

    /*
     * =================================================
     * FRAGMENT OVERRIDES
     */
    public FragmentKeyReceiver() {
        super();

        setStyle(STYLE_NO_FRAME, R.style.App_Dialog);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    @Override
    public View onCreateDialogView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_key_receiver_layout, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSummaryView = (TextView) view.findViewById(R.id.dialog_summary);
        mTypeResultView = (TextView) view.findViewById(R.id.dialog_type_result);
        mFlagsResultView = (TextView) view.findViewById(R.id.dialog_flags_result);
        mPosBtn = view.findViewById(R.id.button_positive);
        mNegBtn = view.findViewById(R.id.button_negative);
    }

    @Override
    public void onResume() {
        super.onResume();

        onInterceptChanged(false);

        BackendServiceMgr backendMgr = getBackendMgr();

        if (backendMgr != null && backendMgr.isServiceReady()) {
            backendMgr.attachListener(this);
            backendMgr.sendListenerMsg(Constants.BRC_PWM_EVENT_REQUEST, new HashBundle("intercept", true));
        }

        mPosBtn.setOnClickListener(this);
        mNegBtn.setOnClickListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        BackendServiceMgr backendMgr = getBackendMgr();

        if (backendMgr != null && backendMgr.isServiceReady()) {
            backendMgr.detachListener(this);
            backendMgr.sendListenerMsg(Constants.BRC_PWM_EVENT_REQUEST, new HashBundle("intercept", false));
        }

        mPosBtn.setOnClickListener(null);
        mNegBtn.setOnClickListener(null);
    }

    /*
     * =================================================
     * INTERNAL METHODS
     */

    private void onInterceptChanged(boolean intercept) {
        if (intercept) {
            mSummaryView.setText(R.string.key_receiver_summary_ready);

        } else {
            mSummaryView.setText(R.string.key_receiver_summary_failed);
        }
    }


    /*
     * =================================================
     * INTERFACE OVERRIDES
     */

    @Override
    public void onReceiveMsg(int type, HashBundle data) {
        switch (type) {
            case Constants.BRC_PWM_EVENT_RESPONSE:

                if (data.getBoolean("ping")) {
                    onInterceptChanged(data.getBoolean("intercept"));

                } else {
                    mKeyCode = data.getInt("keyCode");
                    mDeviceFlags = data.getInt("deviceFlags");

                    String keyName = InputDeviceInfo.keyToLabel(getActivity(), mKeyCode);
                    String keyFlags = InputDeviceInfo.flagsToLabel(getActivity(), mDeviceFlags);

                    mTypeResultView.setText("" + mKeyCode);
                    mFlagsResultView.setText(keyName + (!"".equals(keyFlags) ? "," + keyFlags : ""));
                }
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mNegBtn) {
            dismiss();

        } else if (v == mPosBtn) {
            if (mKeyCode > 0) {
                HashBundle data = new HashBundle();
                data.put("keyCode", mKeyCode);
                data.put("deviceFlags", mDeviceFlags);

                sendMessage(Constants.MSG_DIALOG_KEY_RECEIVER, data, false);
            }
        }
    }
}
