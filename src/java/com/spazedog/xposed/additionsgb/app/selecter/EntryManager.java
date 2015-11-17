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


import android.graphics.Bitmap;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.xposed.additionsgb.app.selecter.EntryManager.Adapter;
import com.spazedog.xposed.additionsgb.app.selecter.EntryManager.Entry;

public abstract class EntryManager<TM extends EntryManager, TE extends Entry, TA extends Adapter> {

    public static abstract class Adapter<TA extends Adapter, TV extends Adapter.ViewHolder, TM extends EntryManager> extends RecyclerView.Adapter<TV> implements OnClickListener {

        public static abstract class ViewHolder<TV extends ViewHolder> extends RecyclerView.ViewHolder {
            public ViewHolder(ViewGroup view) {
                super(view);
            }
        }

        public abstract TM getManager();
    }

    public static abstract class Entry<TE extends Entry, TM extends EntryManager> implements Comparable<TE> {
        public abstract String getLabel();
        public abstract String getName();
        public abstract Bitmap getIcon();
        public abstract int getType();
        public abstract TM getManager();

        public abstract void onClick();
        public abstract void onMsgReceive(int type, HashBundle data);
    }

    public abstract View getAdapterView();
    public abstract TA getAdaptor();
    public abstract TE getEntry(int position);
    public abstract int getEntrySize();
    public abstract int getType();
    public abstract String getLabel();
    public abstract Selecter getSelecter();
    public abstract void setMsgReceiver(TE entry);

    public abstract void onLoad();
    public abstract void onCreate();
    public abstract void onDestroy();
    public abstract void onMsgReceive(int type, HashBundle data);
}
