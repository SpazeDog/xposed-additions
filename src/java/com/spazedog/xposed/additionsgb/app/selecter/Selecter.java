package com.spazedog.xposed.additionsgb.app.selecter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.util.LruCache;

import com.spazedog.lib.utilsLib.HashBundle;

public interface Selecter {

    /*
     * Argument keys for when parsing arguments to the dialog
     */
    public final String ARGS_FLAGS = "flags";
    public final String ARGS_SELECTED = "selected";
    public final String ARGS_REQUEST = "request";

    /*
     * Extras Keys from the HashMap parsed via the message system from this dialog
     */
    public final String EXTRAS_TYPE = "type";
    public final String EXTRAS_URI = "uri";
    public final String EXTRAS_PKG = "package";
    public final String EXTRAS_PKGS = "packages";
    public final String EXTRAS_PLUGIN = "plugin";
    public final String EXTRAS_KEYCODE = "key";
    public final String EXTRAS_RESPONSE = "response";

    public Context getContext();
    public LruCache<String, Bitmap> getImageCache();
    public void sendMessage(int type, HashBundle data);
    public HashBundle getArgs();
    public void requestActivityResult(Intent intent, int responseCode);
}
