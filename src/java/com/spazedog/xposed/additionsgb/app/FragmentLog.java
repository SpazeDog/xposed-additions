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
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
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
import android.widget.ImageView;
import android.widget.TextView;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.lib.utilsLib.utils.Conversion;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.ActivityMain.ActivityMainFragment;
import com.spazedog.xposed.additionsgb.backend.LogcatMonitor.LogcatEntry;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr.ServiceListener;
import com.spazedog.xposed.additionsgb.utils.Constants;

import java.util.List;

public class FragmentLog extends ActivityMainFragment implements ServiceListener {

    protected List<LogcatEntry> mLogEntries = new SparseList<LogcatEntry>();

    protected RecyclerView mRecyclerView;
    protected RecyclerView.LayoutManager mRecyclerManager;
    protected RecyclerView.Adapter mRecyclerAdapter;

    protected Snackbar mSnackBar;


    /*
     * =================================================
     * FRAGMENT OVERRIDES
     */

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

        mRecyclerView = (RecyclerView) view.findViewById(R.id.scrollView);
        mRecyclerManager = new LinearLayoutManager(getActivity());
        mRecyclerAdapter = new LogAdapter();

        mRecyclerView.setLayoutManager(mRecyclerManager);
        mRecyclerView.setAdapter(mRecyclerAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();

        updateLogcatEntries();

        BackendServiceMgr backendMgr = getBackendMgr();

        if (backendMgr != null) {
            backendMgr.attachListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        BackendServiceMgr backendMgr = getBackendMgr();

        if (backendMgr != null) {
            backendMgr.detachListener(this);
        }
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
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:" + Constants.SUPPORT_EMAIL));
                intent.putExtra(Intent.EXTRA_SUBJECT, "XposedAdditions: Error Reporting");
                intent.putExtra(Intent.EXTRA_TEXT, assambleLogInfo());

                List<ResolveInfo> activities = getActivity().getPackageManager().queryIntentActivities(intent, 0);

                if (!activities.isEmpty()) {
                    startActivity(Intent.createChooser(intent, getResources().getString(R.string.menu_title_rapport)));

                } else {
                    if (mSnackBar != null) {
                        mSnackBar.dismiss();
                    }

                    mSnackBar = Snackbar.make(getView(), R.string.notify_missing_mail_client, Snackbar.LENGTH_LONG);
                    mSnackBar.show();
                }

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

    @Override
    public void onReceiveMsg(int type, HashBundle data) {
        switch (type) {
            case Constants.BRC_LOGCAT:
                updateLogcatEntries();
        }
    }

    private void updateLogcatEntries() {
        BackendServiceMgr backendMgr = getBackendMgr();

        if (backendMgr != null && backendMgr.isServiceActive()) {
            List<LogcatEntry> entries = backendMgr.getLogEntries();

            for (int i=entries.size()-1; i >= 0; i--) {
                mLogEntries.add(entries.remove(i));
            }

            mRecyclerAdapter.notifyDataSetChanged();
        }
    }


    /*
     * =================================================
     * RECYCLER VIEW CONTROL
     */

    private static class LogViewHolder extends RecyclerView.ViewHolder {

        public TextView title;
        public TextView summary;
        public TextView time;
        public ImageView icon;

        public LogViewHolder(View itemView) {
            super(itemView);

            title = (TextView) itemView.findViewById(R.id.item_title);
            summary = (TextView) itemView.findViewById(R.id.item_summary);
            time = (TextView) itemView.findViewById(R.id.item_time);
            icon = (ImageView) itemView.findViewById(R.id.item_icon);
        }
    }

    private class LogAdapter extends RecyclerView.Adapter<LogViewHolder> {

        Bitmap mIconDebug;
        Bitmap mIconInfo;
        Bitmap mIconWarning;
        Bitmap mIconError;

        public LogAdapter() {
            Resources res = getResources();
            float iconSize = res.getDimension(R.dimen.logIconSize);

            mIconDebug = Conversion.drawableToBitmap(res.getDrawable(R.drawable.ic_bug_report_black_24dp), iconSize, iconSize);
            mIconInfo = Conversion.drawableToBitmap(res.getDrawable(R.drawable.ic_info_black_24dp), iconSize, iconSize);
            mIconWarning = Conversion.drawableToBitmap(res.getDrawable(R.drawable.ic_warning_black_24dp), iconSize, iconSize);
            mIconError = Conversion.drawableToBitmap(res.getDrawable(R.drawable.ic_feedback_black_24dp), iconSize, iconSize);
        }

        @Override
        public LogViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new LogViewHolder(LayoutInflater.from(getActivity()).inflate(R.layout.fragment_log_item, parent, false));
        }

        @Override
        public void onBindViewHolder(LogViewHolder holder, int position) {
            LogcatEntry entry = mLogEntries.get(position);

            int textColorId = 0;
            Bitmap icon = null;

            switch (entry.Level) {
                case Log.ERROR:
                    textColorId = R.color.logColorError;
                    icon = mIconError;

                    break;

                case Log.WARN:
                    textColorId = R.color.logColorWarning;
                    icon = mIconWarning;

                    break;

                case Log.INFO:
                    textColorId = R.color.logColorInfo;
                    icon = mIconInfo;

                    break;

                case Log.DEBUG:
                    textColorId = R.color.logColorDebug;
                    icon = mIconDebug;
            }

            holder.title.setText(entry.Tag);
            holder.summary.setText(entry.Message);
            holder.time.setText(DateUtils.formatDateTime(getActivity(), entry.Time, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));
            holder.icon.setImageBitmap(icon);

            if (textColorId > 0) {
                holder.summary.setTextColor(getResources().getColor(textColorId));
            }
        }

        @Override
        public void onViewRecycled(LogViewHolder holder) {
            holder.icon.setImageDrawable(null);
        }

        @Override
        public int getItemCount() {
            return mLogEntries != null ? mLogEntries.size() : 0;
        }
    }
}
