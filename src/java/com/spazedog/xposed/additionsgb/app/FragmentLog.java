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


import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.ActivityMain.ActivityMainFragment;
import com.spazedog.xposed.additionsgb.backend.LogcatMonitor.LogcatEntry;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.utils.Constants;

import java.util.Collections;
import java.util.List;

public class FragmentLog extends ActivityMainFragment {

    protected List<LogcatEntry> mLogEntries = new SparseList<LogcatEntry>();
    protected Snackbar mSnackBar;


    /*
     * =================================================
     * FRAGMENT OVERRIDES
     */

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable("mLogEntries", (SparseList) mLogEntries);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log_layout, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.scrollView);
        RecyclerView.LayoutManager recyclerManager = new LinearLayoutManager(getActivity());
        RecyclerView.Adapter recyclerAdapter = new LogAdapter();

        recyclerView.setLayoutManager(recyclerManager);
        recyclerView.setAdapter(recyclerAdapter);

        BackendServiceMgr backendMgr = getBackendMgr();

        if (savedInstanceState != null && savedInstanceState.containsKey("mLogEntries")) {
            mLogEntries = (SparseList) savedInstanceState.getParcelable("mLogEntries");

        } else if (backendMgr != null && backendMgr.isServiceActive()) {
            List<LogcatEntry> entries = backendMgr.getLogEntries();

            for (int i=entries.size()-1; i >= 0; i--) {
                mLogEntries.add(entries.remove(i));
            }
        }

        recyclerAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mSnackBar != null) {
            mSnackBar.dismiss();
            mSnackBar = null;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.fragment_log_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_raport:
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_EMAIL, new String[] {Constants.SUPPORT_EMAIL});
                intent.putExtra(Intent.EXTRA_SUBJECT, "XposedAdditions: Error Reporting");
                intent.putExtra(Intent.EXTRA_TEXT, assambleLogInfo());

                startActivity(Intent.createChooser(intent, getResources().getString(R.string.menu_title_rapport)));

                return true;
        }

        return super.onOptionsItemSelected(item);
    }


    /*
     * =================================================
     * INTERNAL METHODS
     */

    private String assambleLogInfo() {
        BackendServiceMgr backendMgr = getBackendMgr();
        StringBuilder builder = new StringBuilder();
        Integer versionCode = 0;
        String versionName = "0";
        String signature = "";

        try {
            PackageInfo info = getActivity().getPackageManager().getPackageInfo(Constants.PACKAGE_NAME, PackageManager.GET_SIGNATURES);

            versionCode = info.versionCode;
            versionName = info.versionName;

            Signature[] signatures = info.signatures;
            signature = signatures[0].toCharsString();

        } catch (NameNotFoundException e) {}

        builder.append("Module Version: ");

        if (backendMgr != null && backendMgr.isServiceActive()) {
            builder.append(backendMgr.getServiceVersion());

        } else {
            builder.append("Module Version: Not Loaded");
        }

        builder.append("\r\n");
        builder.append("-----------------\r\n");
        builder.append("App Version: " + versionName + " (\"" + versionCode + "\")\r\n");
        builder.append("App Signature: " + signature + "\r\n");
        builder.append("-----------------\r\n");
        builder.append("Manufacturer: " + Build.MANUFACTURER + "\r\n");
        builder.append("Brand: " + Build.BRAND + "\r\n");
        builder.append("Device: " + Build.DEVICE + "\r\n");
        builder.append("Module: " + Build.MODEL + "\r\n");
        builder.append("Product: " + Build.PRODUCT + "\r\n");
        builder.append("Software: (" + Build.VERSION.SDK_INT + ") " + Build.VERSION.RELEASE + "\r\n");
        builder.append("-----------------\r\n\r\n");

        for (LogcatEntry entry : mLogEntries) {
            builder.append(entry.Level + "/");
            builder.append(entry.Tag + ": ");
            builder.append(entry.Message + "\r\n");
        }

        return builder.toString();
    }


    /*
     * =================================================
     * RECYCLER VIEW CONTROL
     */

    private static class LogViewHolder extends RecyclerView.ViewHolder {

        public TextView title;
        public TextView summary;
        public TextView time;

        public LogViewHolder(View itemView) {
            super(itemView);

            title = (TextView) itemView.findViewById(R.id.item_title);
            summary = (TextView) itemView.findViewById(R.id.item_summary);
            time = (TextView) itemView.findViewById(R.id.item_time);
        }
    }

    private class LogAdapter extends RecyclerView.Adapter<LogViewHolder> {

        @Override
        public LogViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new LogViewHolder(LayoutInflater.from(getActivity()).inflate(R.layout.fragment_log_item, parent, false));
        }

        @Override
        public void onBindViewHolder(LogViewHolder holder, int position) {
            LogcatEntry entry = mLogEntries.get(position);

            int textColorId = 0;

            switch (entry.Level) {
                case Log.ERROR: textColorId = R.color.logColorError; break;
                case Log.WARN: textColorId = R.color.logColorWarning; break;
                case Log.INFO: textColorId = R.color.logColorInfo; break;
                case Log.DEBUG: textColorId = R.color.logColorDebug;
            }

            holder.title.setText(entry.Tag);
            holder.summary.setText(entry.Message);
            holder.time.setText(DateUtils.formatDateTime(getActivity(), entry.Time, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));

            if (textColorId > 0) {
                holder.summary.setTextColor(getResources().getColor(textColorId));
            }
        }

        @Override
        public int getItemCount() {
            return mLogEntries != null ? mLogEntries.size() : 0;
        }
    }
}
