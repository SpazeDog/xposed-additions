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


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.v4.util.LruCache;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.lib.utilsLib.utils.Conversion;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.selecter.UriLauncherEntryManager.Adapter;
import com.spazedog.xposed.additionsgb.app.selecter.UriLauncherEntryManager.Entry;
import com.spazedog.xposed.additionsgb.utils.Constants;

import java.text.Collator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UriLauncherEntryManager extends EntryManager<UriLauncherEntryManager, Entry, Adapter> {


    /*
     * =================================================
     * ADAPTOR CLASS
     */

    public class Adapter extends EntryManager.Adapter<Adapter, Adapter.ViewHolder, UriLauncherEntryManager> {

        public class ViewHolder extends EntryManager.Adapter.ViewHolder<ViewHolder> {

            public final ImageView ICON;
            public final TextView TITLE;
            public final TextView SUMMARY;
            public final View CLICKABLE;

            public ViewHolder(ViewGroup view) {
                super(view);

                ICON = (ImageView) view.findViewById(R.id.item_image);
                TITLE = (TextView) view.findViewById(R.id.item_title);
                SUMMARY = (TextView) view.findViewById(R.id.item_summary);
                CLICKABLE = view.findViewById(R.id.item_clickable);
            }
        }

        protected ViewHolder createViewHolder(ViewGroup view) {
            return new ViewHolder(view);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return createViewHolder((ViewGroup) mLayoutInflater.inflate(R.layout.fragment_selecter_entry, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Entry entry = getManager().getEntry(position);

            holder.ICON.setImageBitmap(entry.getIcon());
            holder.TITLE.setText(entry.getLabel());
            holder.SUMMARY.setText(entry.getName());
            holder.CLICKABLE.setOnClickListener(this);
            holder.CLICKABLE.setTag(R.id.tag_holder, position);
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {
            holder.ICON.setImageDrawable(null);
            holder.CLICKABLE.setOnClickListener(null);
        }

        @Override
        public int getItemCount() {
            return getManager().getEntrySize();
        }

        @Override
        public void onClick(View v) {
            int position = (Integer) v.getTag(R.id.tag_holder);
            Entry entry = getManager().getEntry(position);

            entry.onClick();
        }

        @Override
        public UriLauncherEntryManager getManager() {
            return UriLauncherEntryManager.this;
        }
    }


    /*
     * =================================================
     * ENTRY CLASSES
     */

    public class Entry extends EntryManager.Entry<Entry, UriLauncherEntryManager> {

        protected ActivityInfo mEntryInfo;
        protected String mLabel;

        public Entry(ActivityInfo info) {
            mEntryInfo = info;
        }

        @Override
        public String getLabel() {
            if (mLabel == null) {
                Selecter selecter = getManager().getSelecter();
                Context context = selecter.getContext();
                PackageManager pm = context.getPackageManager();

                mLabel = (String) mEntryInfo.loadLabel(pm);
            }

            return mLabel;
        }

        @Override
        public String getName() {
            return mEntryInfo.packageName;
        }

        @Override
        public Bitmap getIcon() {
            Selecter selecter = getManager().getSelecter();
            LruCache<String, Bitmap> imageCache = selecter.getImageCache();
            Context context = selecter.getContext();
            Resources resources = context.getResources();
            PackageManager pm = context.getPackageManager();
            String id = createIconId();
            Bitmap icon = imageCache.get(id);

            if (icon == null) {
                float size = resources.getDimension(R.dimen.appIconSize);

                imageCache.put(id, (icon = Conversion.drawableToBitmap(createIcon(pm), size, size)));
            }

            return icon;
        }

        protected String createIconId() {
            return mEntryInfo.applicationInfo.uid + "_" + (mEntryInfo.icon > 0 ? mEntryInfo.icon : mEntryInfo.getIconResource());
        }

        protected Drawable createIcon(PackageManager pm) {
            return mEntryInfo.loadIcon(pm);
        }

        @Override
        public int getType() {
            return mTypeFlag;
        }

        @Override
        public UriLauncherEntryManager getManager() {
            return UriLauncherEntryManager.this;
        }

        @Override
        public void onClick() {
            Selecter selecter = getManager().getSelecter();

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName(mEntryInfo.packageName, mEntryInfo.name));

            HashBundle data = new HashBundle();
            data.put(Selecter.EXTRAS_TYPE, getType());
            data.put(Selecter.EXTRAS_URI, intent.toUri(Intent.URI_INTENT_SCHEME));

            selecter.sendMessage(Constants.MSG_DIALOG_SELECTER, data);
        }

        @Override
        public void onMsgReceive(int type, HashBundle data) {

        }

        @Override
        public int compareTo(Entry another) {
            return Collator.getInstance(Locale.getDefault()).compare(getName(), another.getName());
        }
    }


    /*
     * =================================================
     * MANAGER METHODS
     */

    protected int mReceiverEntry = -1;
    protected int mTypeFlag = 0;
    protected Selecter mSelecter;
    protected LayoutInflater mLayoutInflater;
    protected List<Entry> mEntries = new SparseList<Entry>();

    protected ViewGroup mView;
    protected ProgressBar mProgress;
    protected RecyclerView mPageView;
    protected RecyclerView.LayoutManager mLayoutManager;
    protected Adapter mAdapter;

    public UriLauncherEntryManager(Selecter selecter, ViewGroup container, int typeFlag) {
        Context context = selecter.getContext();

        mTypeFlag = typeFlag;
        mSelecter = selecter;

        mLayoutInflater = LayoutInflater.from(context);
        mView = (ViewGroup) mLayoutInflater.inflate(R.layout.fragment_selecter_page, container, false);
        mProgress = (ProgressBar) mView.findViewById(R.id.page_progress);
        mPageView = (RecyclerView) mView.findViewById(R.id.page_view);
        mLayoutManager = new LinearLayoutManager(context);
        mAdapter = getAdaptor();

        mPageView.setAdapter(mAdapter);
        mPageView.setLayoutManager(mLayoutManager);
    }

    @Override
    public View getAdapterView() {
        return mView;
    }

    @Override
    public Adapter getAdaptor() {
        if (mAdapter == null) {
            mAdapter = new Adapter();
        }

        return mAdapter;
    }

    @Override
    public Entry getEntry(int position) {
        return mEntries.get(position);
    }

    @Override
    public int getEntrySize() {
        return mEntries.size();
    }

    @Override
    public int getType() {
        return mTypeFlag;
    }

    @Override
    public String getLabel() {
        Context context = mSelecter.getContext();
        Resources resources = context.getResources();

        return resources.getString(getLabelResId());
    }

    @Override
    public Selecter getSelecter() {
        return mSelecter;
    }

    @Override
    public void setMsgReceiver(Entry entry) {
        if (entry != null && mEntries.contains(entry)) {
            mReceiverEntry = mEntries.indexOf(entry);

        } else {
            mReceiverEntry = -1;
        }
    }

    @Override
    public void onLoad() {
        List<ResolveInfo> activityList = mSelecter.getContext().getPackageManager().queryIntentActivities(getLoadIntent(), 0);

        for (ResolveInfo resolveInfo : activityList) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            Entry entry = createEntry(activityInfo);

            mEntries.add(entry);
        }

        Collections.sort(mEntries);
    }

    @Override
    public void onCreate() {
        mPageView.setVisibility(View.VISIBLE);
        mProgress.setVisibility(View.GONE);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        mLayoutInflater = null;
        mLayoutManager = null;
        mView = null;
        mProgress = null;
        mPageView = null;
        mSelecter = null;

        mEntries.clear();
    }

    @Override
    public void onMsgReceive(int type, HashBundle data) {
        if (mReceiverEntry > 0 && mReceiverEntry < mEntries.size()) {
            Entry entry = mEntries.get(mReceiverEntry);

            if (entry != null) {
                entry.onMsgReceive(type, data);
            }
        }
    }


    /*
     * =================================================
     * PRIVATE METHODS
     */

    protected int getLabelResId() {
        return R.string.selecter_page_title_launcher;
    }

    protected Entry createEntry(ActivityInfo activityInfo) {
        return new Entry(activityInfo);
    }

    protected Intent getLoadIntent() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        return intent;
    }
}
