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


import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.lib.utilsLib.SparseMap;
import com.spazedog.lib.utilsLib.app.MsgContext;
import com.spazedog.lib.utilsLib.app.MsgContext.MsgContextListener;
import com.spazedog.lib.utilsLib.os.ThreadHandler;
import com.spazedog.lib.utilsLib.utils.Conversion;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.ActivityMain.ActivityMainDialog;
import com.spazedog.xposed.additionsgb.utils.Constants;

import java.text.Collator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
 * TODO:
 *      1:    Currently this is a rough version of the launcher dialog,
 *            and it does not yet take into consideration if someone closes the dialog
 *            before items are done loading. This might leed to crashes since
 *            onDestroy cleans up some properties used by both handlers.
 *            Make sure to fix this.
 *
 *      2:    Should we update all of the lists when applications are
 *            installed, updated or removed?
 */
public class FragmentLaunchSelector extends ActivityMainDialog {

    /*
     * Flags indicating what type of URI's to create
     */
    public static final int URI_ALL = 0x00000007;       // URI_LAUNCHER|URI_ACTIVITY|URI_SHORTCUT
    public static final int URI_LAUNCHER = 0x00000001;
    public static final int URI_ACTIVITY = 0x00000002;
    public static final int URI_SHORTCUT = 0x00000004;

    /*
     * Flags indicating what application selection type to create.
     */
    public static final int APP_SINGLE = 0x80000000;
    public static final int APP_MULTI = 0x40000000;

    /*
     * Argument keys for when parsing arguments to the dialog
     */
    public static final String ARGS_FLAGS = "flags";
    public static final String ARGS_SELECTED = "selected";

    /*
     * Extras Keys from the HashMap parsed via the message system from this dialog
     */
    public static final String EXTRAS_TYPE = "type";
    public static final String EXTRAS_URI = "uri";
    public static final String EXTRAS_PKG = "package";
    public static final String EXTRAS_PKGS = "packages";

    protected int mFlags = 0;
    protected List<String> mSelected;

    protected ForegroundHandler mForegroundHandler;
    protected BackgroundHandler mBackgroundHandler;

    protected LayoutInflater mLayoutInflater;
    protected Context mContext;
    protected PackageManager mPackageManager;

    protected LruCache<String, Bitmap> mImageCache;

    protected EntryPagerAdaptor mEntryAdaptor;
    protected ViewPager mEntryView;


    /*
     * =================================================
     * FRAGMENT OVERRIDES
     */

    public FragmentLaunchSelector() {
        super();

        setStyle(STYLE_NO_FRAME, R.style.App_Dialog);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        HashBundle bundle = getArgs();
        mFlags = bundle.getInt(ARGS_FLAGS, URI_ALL);
        mSelected = bundle.getStringList(ARGS_SELECTED);

        mForegroundHandler = new ForegroundHandler();
        mBackgroundHandler = new BackgroundHandler();

        mLayoutInflater = getLayoutInflater(savedInstanceState);
        mContext = getActivity().getApplicationContext();
        mPackageManager = mContext.getPackageManager();

        mImageCache = new LruCache<String, Bitmap>( Math.round(0.15f * Runtime.getRuntime().maxMemory() / 1024) ) {
            @Override
            protected int sizeOf(String key, Bitmap image) {
                return (image.getRowBytes() * image.getHeight()) / 1024;
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mEntryAdaptor.onExit();
        mBackgroundHandler.close();

        mLayoutInflater = null;
        mPackageManager = null;
        mContext = null;
        mBackgroundHandler = null;
        mForegroundHandler = null;
    }

    @Override
    public View onCreateWindowView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_selector_layout, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        List<Integer> typeList = new SparseList<Integer>();
        for (int i=0; i < 32; i++) {
            int type = 1 << i;

            if ((mFlags & type) != 0) {
                typeList.add(type);
            }
        }

        mEntryAdaptor = new EntryPagerAdaptor(typeList);
        mEntryView = (ViewPager) view.findViewById(R.id.pager);
        mEntryView.setAdapter(mEntryAdaptor);

        /*
         * TODO:
         *      The support libraries, as useal, has issues.
         *      The TitleStrip is not properly initiated and will therefore
         *      not show any titles until the first page switch.
         *
         *      The below hack fixes this, as long as there is at least two pages to display.
         *      If not, we simply hide the strip for now.
         *
         *      This should be removed ones Google finally fixes it.
         */
        if (typeList.size() > 1) {
            mEntryView.post(new Runnable() {
                @Override
                public void run() {
                    mEntryView.setCurrentItem(1);
                    mEntryView.post(new Runnable() {
                        @Override
                        public void run() {
                            mEntryView.setCurrentItem(0);
                        }
                    });
                }
            });

        } else {
            mEntryView.findViewById(R.id.pager_title).setVisibility(View.GONE);
        }
    }

    /*
     * =================================================
     * HANDLER CLASSES
     */

    protected class ForegroundHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            EntryManager manager = mEntryAdaptor.getManager(msg.what);

            if (manager != null) {
                manager.updateEntries((List<ItemEntry>) msg.obj);
            }
        }
    }

    protected class BackgroundHandler extends ThreadHandler {
        public BackgroundHandler() {
            super("ApplicationLoader");
        }

        @Override
        public void handleMessage(Message msg) {
            List<ItemEntry> list = new SparseList<ItemEntry>();

            if ((URI_ALL & msg.what) != 0) {
                Intent intent = new Intent();

                if (msg.what != URI_SHORTCUT) {
                    intent.setAction(Intent.ACTION_MAIN);

                    if (msg.what == URI_LAUNCHER) {
                        intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    }

                } else {
                    intent.setAction(Intent.ACTION_CREATE_SHORTCUT);
                }

                List<ResolveInfo> activityList = mPackageManager.queryIntentActivities(intent, 0);

                for (ResolveInfo resolveInfo : activityList) {
                    ActivityInfo activityInfo = resolveInfo.activityInfo;
                    ItemEntry entry = null;

                    switch (msg.what) {
                        case URI_LAUNCHER: entry = new LauncherEntry(activityInfo); break;
                        case URI_ACTIVITY: entry = new ActivityEntry(activityInfo); break;
                        case URI_SHORTCUT: entry = new ShortcutEntry(activityInfo);
                    }

                    list.add(entry);
                }

            } else {
                List<ApplicationInfo> appList = mPackageManager.getInstalledApplications(0);

                for (ApplicationInfo appInfo : appList) {
                    ItemEntry entry = null;

                    switch (msg.what) {
                        case APP_SINGLE: entry = new SingleAppEntry(appInfo); break;
                        case APP_MULTI: entry = new MultiAppEntry(appInfo);
                    }

                    list.add(entry);
                }
            }

            Collections.sort(list);

            mForegroundHandler.sendMessage(
                    mForegroundHandler.obtainMessage(msg.what, list)
            );
        }
    }


    /*
     * =================================================
     * CONTAINER CLASSES
     */

    protected interface ItemEntry extends Comparable<ItemEntry> {
        public String getLabel();
        public String getName();
        public Bitmap getIcon();
        public void onClick();
        public int getType();
    }

    protected interface MultiItemEntry extends ItemEntry {
        public boolean isSelected();
    }

    protected class LauncherEntry implements ItemEntry {

        protected ActivityInfo mActivityInfo;
        protected String mLabel;

        public LauncherEntry(ActivityInfo activityInfo) {
            mActivityInfo = activityInfo;
        }

        @Override
        public String getLabel() {
            if (mLabel == null) {
                mLabel = (String) mActivityInfo.loadLabel(mPackageManager);
            }

            return mLabel;
        }

        @Override
        public String getName() {
            return mActivityInfo.packageName;
        }

        @Override
        public Bitmap getIcon() {
            String id = mActivityInfo.applicationInfo.uid + "_" + (mActivityInfo.icon > 0 ? mActivityInfo.icon : mActivityInfo.getIconResource());
            Bitmap icon = mImageCache.get(id);

            if (icon == null) {
                float size = getResources().getDimension(R.dimen.appIconSize);

                mImageCache.put(id, (icon = Conversion.drawableToBitmap(mActivityInfo.loadIcon(mPackageManager), size, size)));
            }

            return icon;
        }

        @Override
        public void onClick() {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName(mActivityInfo.packageName, mActivityInfo.name));

            HashBundle data = new HashBundle();
            data.put(EXTRAS_TYPE, getType());
            data.put(EXTRAS_URI, intent.toUri(Intent.URI_INTENT_SCHEME));

            sendMessage(Constants.MSG_DIALOG_SELECTOR, data, false);
        }

        @Override
        public int compareTo(ItemEntry another) {
            return Collator.getInstance(Locale.getDefault()).compare(getName(), another.getName());
        }

        @Override
        public int getType() {
            return URI_LAUNCHER;
        }
    }

    protected class ActivityEntry extends LauncherEntry {

        public ActivityEntry(ActivityInfo activityInfo) {
            super(activityInfo);
        }

        @Override
        public String getName() {
            return mActivityInfo.name;
        }

        @Override
        public int getType() {
            return URI_ACTIVITY;
        }
    }

    protected class ShortcutEntry extends ActivityEntry implements MsgContextListener {

        protected MsgContext mMsgContext;

        public ShortcutEntry(ActivityInfo activityInfo) {
            super(activityInfo);
        }

        @Override
        public int getType() {
            return URI_SHORTCUT;
        }

        @Override
        public void onClick() {
            if (mMsgContext == null) {
                mMsgContext = new MsgContext(mContext);
                mMsgContext.setMsgListener(this);
            }

            Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
            intent.setComponent(new ComponentName(mActivityInfo.packageName, mActivityInfo.name));

            getActivity().startActivityForResult(intent, Constants.RESULT_ACTION_PARSE_SHORTCUT);
        }

        @Override
        public void onReceiveMessage(int type, HashBundle data, boolean sticky, int event) {
            if (type == Constants.MSG_ACTIVITY_RESULT) {
                int result = data.getInt("resultCode");
                int request = data.getInt("requestCode");

                if (request == Constants.RESULT_ACTION_PARSE_SHORTCUT) {
                    if (result == Activity.RESULT_OK) {
                        Intent intent = (Intent) data.getParcelable("intent");

                        if (intent != null) {
                            Intent appIntent = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);

                            HashBundle appData = new HashBundle();
                            appData.put(EXTRAS_TYPE, getType());
                            appData.put(EXTRAS_URI, appIntent.toUri(Intent.URI_INTENT_SCHEME));

                            sendMessage(Constants.MSG_DIALOG_SELECTOR, appData, false);
                        }
                    }

                    mMsgContext.setMsgListener(null);
                    mMsgContext = null;
                }
            }
        }
    }

    protected class SingleAppEntry implements ItemEntry {

        protected ApplicationInfo mApplicationInfo;
        protected String mLabel;

        public SingleAppEntry(ApplicationInfo appInfo) {
            mApplicationInfo = appInfo;
        }

        @Override
        public String getLabel() {
            if (mLabel == null) {
                mLabel = (String) mApplicationInfo.loadLabel(mPackageManager);
            }

            return mLabel;
        }

        @Override
        public String getName() {
            return mApplicationInfo.packageName;
        }

        @Override
        public Bitmap getIcon() {
            String id = mApplicationInfo.uid + "_" + mApplicationInfo.icon;
            Bitmap icon = mImageCache.get(id);

            if (icon == null) {
                float size = getResources().getDimension(R.dimen.appIconSize);

                mImageCache.put(id, (icon = Conversion.drawableToBitmap(mApplicationInfo.loadIcon(mPackageManager), size, size)));
            }

            return icon;
        }

        @Override
        public void onClick() {
            HashBundle data = new HashBundle();
            data.put(EXTRAS_TYPE, getType());
            data.put(EXTRAS_PKG, getName());

            sendMessage(Constants.MSG_DIALOG_SELECTOR, data, false);
        }

        @Override
        public int getType() {
            return APP_SINGLE;
        }

        @Override
        public int compareTo(ItemEntry another) {
            return Collator.getInstance(Locale.getDefault()).compare(getName(), another.getName());
        }
    }

    protected class MultiAppEntry extends SingleAppEntry implements MultiItemEntry {

        protected boolean mIsSelected = false;

        public MultiAppEntry(ApplicationInfo appInfo) {
            super(appInfo);

            if (mSelected != null) {
                mIsSelected = mSelected.contains(appInfo.packageName);
            }
        }

        @Override
        public int getType() {
            return APP_MULTI;
        }

        @Override
        public boolean isSelected() {
            return mIsSelected;
        }

        @Override
        public void onClick() {
            mIsSelected = !mIsSelected;
        }
    }


    /*
     * =================================================
     * ADAPTOR CLASSES
     */

    protected class EntryPagerAdaptor extends PagerAdapter {

        protected List<Integer> mTypeList;
        protected Map<Integer, EntryManager> mEntryManagers = new SparseMap<EntryManager>();

        public EntryPagerAdaptor(List<Integer> typeList) {
            mTypeList = typeList;
        }

        @Override
        public int getCount() {
            return mTypeList.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            int type = mTypeList.get(position);
            EntryManager manager = mEntryManagers.get(type);

            if (manager == null) {
                switch (type) {
                    case APP_MULTI: mEntryManagers.put(type, (manager = new MultiEntryManager(type, container))); break;
                    default: mEntryManagers.put(type, (manager = new EntryManager(type, container)));
                }

                if (!mBackgroundHandler.hasMessages(type)) {
                    mBackgroundHandler.sendEmptyMessage(type);
                }
            }

            container.addView(manager.getView());

            return manager.getView();
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        public EntryManager getManager(int type) {
            return mEntryManagers.get(type);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            int type = mTypeList.get(position);
            EntryManager manager = mEntryManagers.get(type);

            if (manager != null) {
                return manager.getLabel();
            }

            return "";
        }

        public void onExit() {
            for (EntryManager manager : mEntryManagers.values()) {
                manager.onExit();
            }
        }
    }


    /*
     * =================================================
     * PAGE CLASSES
     */

    protected class EntryManager {

        protected int mType;

        protected ViewGroup mView;
        protected ProgressBar mProgress;
        protected RecyclerView mPageView;
        protected RecyclerView.Adapter mAdapter;
        protected RecyclerView.LayoutManager mLayoutManager;

        protected List<ItemEntry> mEntries;

        public EntryManager(int type, ViewGroup container) {
            mType = type;

            mView = (ViewGroup) mLayoutInflater.inflate(R.layout.fragment_selector_page, container, false);
            mProgress = (ProgressBar) mView.findViewById(R.id.page_progress);
            mPageView = (RecyclerView) mView.findViewById(R.id.page_view);
            mLayoutManager = new LinearLayoutManager(mContext);
            mAdapter = onCreateAdaptor();

            mPageView.setAdapter(mAdapter);
            mPageView.setLayoutManager(mLayoutManager);
        }

        protected class EntryAdaptor extends RecyclerView.Adapter<EntryViewHolder> implements OnClickListener {

            @Override
            public EntryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return new EntryViewHolder((ViewGroup) mLayoutInflater.inflate(R.layout.fragment_selector_entry, parent, false));
            }

            @Override
            public void onViewRecycled(EntryViewHolder holder) {
                holder.ICON.setImageDrawable(null);
                holder.CLICKABLE.setOnClickListener(null);
                holder.CLICKABLE.setTag(R.id.tag_holder, null);
                holder.CLICKABLE.setTag(R.id.tag_data, null);
            }

            @Override
            public void onBindViewHolder(EntryViewHolder holder, int position) {
                ItemEntry entry = mEntries.get(position);

                holder.ICON.setImageBitmap(entry.getIcon());
                holder.TITLE.setText(entry.getLabel());
                holder.SUMMARY.setText(entry.getName());
                holder.CLICKABLE.setOnClickListener(this);
                holder.CLICKABLE.setTag(R.id.tag_holder, holder);
                holder.CLICKABLE.setTag(R.id.tag_data, entry);
            }

            @Override
            public int getItemCount() {
                return mEntries != null ? mEntries.size() : 0;
            }

            @Override
            public void onClick(View v) {
                ItemEntry entry = (ItemEntry) v.getTag(R.id.tag_data);
                entry.onClick();
            }
        }

        protected class EntryViewHolder extends RecyclerView.ViewHolder {

            public final ImageView ICON;
            public final TextView TITLE;
            public final TextView SUMMARY;
            public final View CLICKABLE;

            public EntryViewHolder(ViewGroup view) {
                super(view);

                ICON = (ImageView) view.findViewById(R.id.item_image);
                TITLE = (TextView) view.findViewById(R.id.item_title);
                SUMMARY = (TextView) view.findViewById(R.id.item_summary);
                CLICKABLE = view.findViewById(R.id.item_clickable);
            }
        }

        protected RecyclerView.Adapter onCreateAdaptor() {
            return new EntryAdaptor();
        }

        public View getView() {
            return mView;
        }

        public void updateEntries(List<ItemEntry> entries) {
            mPageView.setVisibility(View.VISIBLE);
            mProgress.setVisibility(View.GONE);
            mEntries = entries;
            mAdapter.notifyDataSetChanged();
        }

        public int getType() {
            return mType;
        }

        public String getLabel() {
            switch (mType) {
                case URI_LAUNCHER: return "Applications";
                case URI_ACTIVITY: return "Activities";
                case URI_SHORTCUT: return "Shortcuts";
                default: return "";
            }
        }

        public void onExit() {

        }
    }

    protected class MultiEntryManager extends EntryManager {

        public MultiEntryManager(int type, ViewGroup container) {
            super(type, container);
        }

        protected class MultiEntryAdaptor extends EntryAdaptor {

            @Override
            public EntryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return new MultiEntryViewHolder((ViewGroup) mLayoutInflater.inflate(R.layout.fragment_selector_entry, parent, false));
            }

            @Override
            public void onBindViewHolder(EntryViewHolder holder, int position) {
                super.onBindViewHolder(holder, position);

                MultiItemEntry entry = (MultiItemEntry) mEntries.get(position);
                MultiEntryViewHolder multiHolder = (MultiEntryViewHolder) holder;

                multiHolder.CHECKBOX.setChecked(entry.isSelected());
            }

            @Override
            public void onClick(View v) {
                super.onClick(v);

                MultiEntryViewHolder holder = (MultiEntryViewHolder) v.getTag(R.id.tag_holder);
                MultiItemEntry entry = (MultiItemEntry) v.getTag(R.id.tag_data);

                holder.CHECKBOX.setChecked(entry.isSelected());
            }
        }

        protected class MultiEntryViewHolder extends EntryViewHolder {

            public final CheckBox CHECKBOX;

            public MultiEntryViewHolder(ViewGroup view) {
                super(view);

                CHECKBOX = (CheckBox) view.findViewById(R.id.item_checkbox);
                CHECKBOX.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected Adapter onCreateAdaptor() {
            return new MultiEntryAdaptor();
        }

        @Override
        public void onExit() {
            List<String> pkgs = new SparseList<String>();
            for (ItemEntry entry : mEntries) {
                if (((MultiItemEntry) entry).isSelected()) {
                    pkgs.add(entry.getName());
                }
            }

            HashBundle data = new HashBundle();
            data.put(EXTRAS_TYPE, getType());
            data.putStringList(EXTRAS_PKGS, pkgs);

            sendMessage(Constants.MSG_DIALOG_SELECTOR, data, false);
        }
    }
}
