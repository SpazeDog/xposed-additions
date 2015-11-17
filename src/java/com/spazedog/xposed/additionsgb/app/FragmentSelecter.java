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


import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.lib.utilsLib.SparseMap;
import com.spazedog.lib.utilsLib.os.ThreadHandler;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.ActivityMain.ActivityMainDialog;
import com.spazedog.xposed.additionsgb.app.selecter.AppMultiEntryManager;
import com.spazedog.xposed.additionsgb.app.selecter.AppSingleEntryManager;
import com.spazedog.xposed.additionsgb.app.selecter.EntryManager;
import com.spazedog.xposed.additionsgb.app.selecter.Selecter;
import com.spazedog.xposed.additionsgb.app.selecter.UriActivityEntryManager;
import com.spazedog.xposed.additionsgb.app.selecter.UriLauncherEntryManager;
import com.spazedog.xposed.additionsgb.app.selecter.UriShortcutEntryManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FragmentSelecter extends ActivityMainDialog {

    /*
     * Flags indicating what type of URI's to create
     */
    public static final int URI_ALL = 0x00000007;       // URI_LAUNCHER|URI_ACTIVITY|URI_SHORTCUT
    public static final int URI_LAUNCHER = 0x00000001;
    public static final int URI_ACTIVITY = 0x00000002;
    public static final int URI_SHORTCUT = 0x00000004;
    // public static final int URI_TASKER = 0x;


    /*
     * Flags indicating what application selection type to create.
     */
    public static final int APP_SINGLE = 0x80000000;
    public static final int APP_MULTI = 0x40000000;


    /*
     * Flags indicating what action selection type to create.
     */

    // public static final int ACTION_ALL = 0x;         // ACTION_KEYCODE|ACTION_TASK
    // public static final int ACTION_KEYCODE = 0x;
    // public static final int ACTION_TASK = 0x;


    private Selecter mCallback = new Selecter() {

        @Override
        public Context getContext() {
            return FragmentSelecter.this.getActivity();
        }

        @Override
        public LruCache<String, Bitmap> getImageCache() {
            return FragmentSelecter.this.mImageCache;
        }

        @Override
        public void sendMessage(int type, HashBundle data) {
            FragmentSelecter.this.sendMessage(type, data, false);
        }

        @Override
        public HashBundle getArgs() {
            return FragmentSelecter.this.getArgs();
        }

        @Override
        public void requestActivityResult(Intent intent, int responseCode) {
            FragmentSelecter.this.getActivity().startActivityForResult(intent, responseCode);
        }
    };


    protected int mFlags = 0;
    protected LruCache<String, Bitmap> mImageCache;
    protected EntryPagerAdaptor mEntryAdaptor;
    protected ViewPager mEntryView;

    protected ForegroundHandler mForegroundHandler;
    protected BackgroundHandler mBackgroundHandler;


    /*
     * =================================================
     * FRAGMENT OVERRIDES
     */

    public FragmentSelecter() {
        super();

        setStyle(STYLE_NO_FRAME, R.style.App_Dialog);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        HashBundle bundle = getArgs();
        mFlags = bundle.getInt(Selecter.ARGS_FLAGS, URI_ALL);

        mImageCache = new LruCache<String, Bitmap>( Math.round(0.15f * Runtime.getRuntime().maxMemory() / 1024) ) {
            @Override
            protected int sizeOf(String key, Bitmap image) {
                return (image.getRowBytes() * image.getHeight()) / 1024;
            }
        };

        mForegroundHandler = new ForegroundHandler();
        mBackgroundHandler = new BackgroundHandler();
    }

    @Override
    public View onCreateWindowView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_selecter_layout, container, false);
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

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mEntryAdaptor != null) {
            for (EntryManager manager : mEntryAdaptor.getManagers()) {
                manager.onDestroy();
            }
        }

        mBackgroundHandler = null;
        mForegroundHandler = null;
    }

    @Override
    public void onReceiveMessage(int type, HashBundle data, boolean isSticky) {
        if (mEntryAdaptor != null) {
            for (EntryManager manager : mEntryAdaptor.getManagers()) {
                manager.onMsgReceive(type, data);
            }
        }
    }


    /*
     * =================================================
     * HANDLERS
     */

    protected class ForegroundHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            int type = msg.what;
            EntryManager manager = mEntryAdaptor.getManager(type);
            manager.onCreate();
        }
    }

    protected class BackgroundHandler extends ThreadHandler {
        public BackgroundHandler() {
            super("ApplicationLoader");
        }

        @Override
        public void handleMessage(Message msg) {
            int type = msg.what;
            EntryManager manager = mEntryAdaptor.getManager(type);
            manager.onLoad();

            if (mForegroundHandler != null) {
                mForegroundHandler.sendEmptyMessage(type);
            }
        }
    }


    /*
     * =================================================
     * PAGER ADAPTER
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
                    case URI_LAUNCHER: manager = new UriLauncherEntryManager(mCallback, container, type); break;
                    case URI_ACTIVITY: manager = new UriActivityEntryManager(mCallback, container, type); break;
                    case URI_SHORTCUT: manager = new UriShortcutEntryManager(mCallback, container, type); break;
                    case APP_SINGLE: manager = new AppSingleEntryManager(mCallback, container, type); break;
                    case APP_MULTI: manager = new AppMultiEntryManager(mCallback, container, type); break;
                    default: return null;
                }

                mEntryManagers.put(type, manager);
                mBackgroundHandler.sendEmptyMessage(type);
            }

            container.addView(manager.getAdapterView());

            return manager.getAdapterView();
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        public EntryManager getManager(int type) {
            return mEntryManagers.get(type);
        }

        public Collection<EntryManager> getManagers() {
            return mEntryManagers.values();
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
    }
}
