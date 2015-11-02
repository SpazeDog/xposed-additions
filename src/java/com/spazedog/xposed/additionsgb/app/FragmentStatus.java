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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.spazedog.xposed.additionsgb.BuildConfig;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.ActivityMain.ActivityMainFragment;
import com.spazedog.xposed.additionsgb.backend.LogcatMonitor.LogcatEntry;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;

import java.util.List;

public class FragmentStatus extends ActivityMainFragment {


    /*
     * =================================================
     * FRAGMENT OVERRIDES
     */

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_status_layout, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BackendServiceMgr backendMgr = getBackendMgr();

        if (backendMgr != null && backendMgr.isServiceActive()) {
            TextView logErrorView = (TextView) view.findViewById(R.id.status_log_errors_item);
            TextView logWarningView = (TextView) view.findViewById(R.id.status_log_warnings_item);
            List<LogcatEntry> entries = backendMgr.getLogEntries();

            int errorCount = 0;
            int warningCount = 0;

            for (LogcatEntry entry : entries) {
                switch (entry.Level) {
                    case Log.ERROR: errorCount++; break;
                    case Log.WARN: warningCount++; break;
                }
            }

            logErrorView.setText("" + errorCount);
            logWarningView.setText("" + warningCount);
        }

        TextView backendView = (TextView) view.findViewById(R.id.status_backend_service_item);

        if (backendMgr != null && backendMgr.isServiceReady() && BuildConfig.VERSION_CODE != backendMgr.getServiceVersion()) {
            backendView.setText(R.string.status_backend_info_updated);
            backendView.setTextColor(getResources().getColor(R.color.logColorWarning));

        } if (backendMgr != null && backendMgr.isServiceReady()) {
            backendView.setText(R.string.status_backend_info_ready);
            backendView.setTextColor(getResources().getColor(R.color.logColorInfo));

        } else if (backendMgr != null && backendMgr.isServiceActive()) {
            backendView.setText(R.string.status_backend_info_active);
            backendView.setTextColor(getResources().getColor(R.color.logColorWarning));

        } else {
            backendView.setText(R.string.status_backend_info_missing);
            backendView.setTextColor(getResources().getColor(R.color.logColorError));
        }
    }
}
