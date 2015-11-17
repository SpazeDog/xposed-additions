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


import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.view.ViewGroup;
import android.widget.Toast;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.utils.Constants;

import net.dinglisch.android.tasker.TaskerIntent;

import java.util.List;

public class UriShortcutEntryManager extends UriLauncherEntryManager {

    public UriShortcutEntryManager(Selecter selecter, ViewGroup container, int typeFlag) {
        super(selecter, container, typeFlag);
    }

    public class Entry extends UriLauncherEntryManager.Entry {

        protected boolean mIsTasker = false;
        protected boolean mIsTaskerEnabled = false;

        public Entry(ActivityInfo info) {
            super(info);
        }

        public Entry(ActivityInfo info, boolean isTasker) {
            super(info);

            mIsTasker = isTasker;

            if (isTasker) {
                mIsTaskerEnabled = TaskerIntent.testStatus(getSelecter().getContext()).equals(TaskerIntent.Status.OK);
            }
        }

        @Override
        public void onClick() {
            Selecter selecter = getSelecter();
            Intent intent = null;

            if (mIsTasker && !mIsTaskerEnabled) {
                Toast.makeText(selecter.getContext(), R.string.notify_tasker_external_disabled, Toast.LENGTH_LONG).show(); return;

            } else if (mIsTasker) {
                intent = TaskerIntent.getTaskSelectIntent();

            } else {
                intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                intent.setComponent(new ComponentName(mEntryInfo.packageName, mEntryInfo.name));
            }

            getManager().setMsgReceiver(this);
            selecter.requestActivityResult(intent, Constants.RESULT_ACTION_PARSE_SHORTCUT);
        }

        @Override
        public void onMsgReceive(int type, HashBundle data) {
            getManager().setMsgReceiver(null);

            if (type == Constants.MSG_ACTIVITY_RESULT) {
                int result = data.getInt("resultCode");
                int request = data.getInt("requestCode");

                if (request == Constants.RESULT_ACTION_PARSE_SHORTCUT) {
                    if (result == Activity.RESULT_OK) {
                        Intent intent = (Intent) data.getParcelable("intent");

                        if (intent != null) {
                            Intent appIntent = null;

                            if (mIsTasker) {
                                appIntent = new TaskerIntent(intent.getDataString());

                            } else {
                                appIntent = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
                            }

                            HashBundle appData = new HashBundle();
                            appData.put(Selecter.EXTRAS_TYPE, getType());
                            appData.put(Selecter.EXTRAS_URI, appIntent.toUri(Intent.URI_INTENT_SCHEME));

                            /*
                             * We need to define this as a Tasker URI, since regular URI's is launched using
                             * startActivity() whereas Tasker Tasks is launched using sendBroadcast()
                             */
                            if (mIsTasker) {
                                appData.put(Selecter.EXTRAS_PLUGIN, "tasker");
                            }

                            getSelecter().sendMessage(Constants.MSG_DIALOG_SELECTER, appData);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onLoad() {
        /*
         * Inject Tasker into the Shortcut list
         */
        List<ResolveInfo> activityList = mSelecter.getContext().getPackageManager().queryIntentActivities(TaskerIntent.getTaskSelectIntent(), 0);

        if (activityList.size() > 0) {
            mEntries.add(
                    new Entry(activityList.get(0).activityInfo, true)
            );
        }

        /*
         * Load regular shortcuts
         */
        super.onLoad();
    }

    @Override
    protected Entry createEntry(ActivityInfo activityInfo) {
        return new Entry(activityInfo);
    }

    @Override
    protected Intent getLoadIntent() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_CREATE_SHORTCUT);

        return intent;
    }

    @Override
    protected int getLabelResId() {
        return R.string.selecter_page_title_shortcut;
    }
}
