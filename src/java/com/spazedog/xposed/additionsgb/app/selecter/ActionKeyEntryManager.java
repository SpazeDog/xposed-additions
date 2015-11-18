package com.spazedog.xposed.additionsgb.app.selecter;


import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.InputDeviceInfo;

import java.util.Collections;
import java.util.Set;

public class ActionKeyEntryManager extends UriLauncherEntryManager {

    public ActionKeyEntryManager(Selecter selecter, ViewGroup container, int typeFlag) {
        super(selecter, container, typeFlag);
    }

    public class Entry extends UriLauncherEntryManager.Entry {

        protected int mKeyCode;

        public Entry(int keyCode) {
            super(null);

            mKeyCode = keyCode;
        }

        @Override
        public String getLabel() {
            return "" + mKeyCode;
        }

        @Override
        public String getName() {
            return InputDeviceInfo.keyToLabel(getSelecter().getContext(), mKeyCode);
        }

        @Override
        protected String createIconId() {
            return "keyCodes";
        }

        @Override
        protected Drawable createIcon(PackageManager pm) {
            return getSelecter().getContext().getResources().getDrawable(R.drawable.ic_keyboard_black_48dp);
        }

        @Override
        public void onClick() {
            Selecter selecter = getManager().getSelecter();

            HashBundle data = new HashBundle();
            data.put(Selecter.EXTRAS_TYPE, getType());
            data.put(Selecter.EXTRAS_KEYCODE, mKeyCode);

            selecter.sendMessage(Constants.MSG_DIALOG_SELECTER, data);
        }
    }

    @Override
    public void onLoad() {
        Set<Integer> keys = InputDeviceInfo.getKeyCodeSet();

        for (int key : keys) {
            mEntries.add(new Entry(key));
        }

        Collections.sort(mEntries);
    }

    protected int getLabelResId() {
        return R.string.selecter_page_title_keycodes;
    }
}
