package com.spazedog.xposed.additionsgb.utils.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ContextHelper {
	@SuppressLint("NewApi")
	public static void sendBroadcast(Context context, Intent intent) {
		if (Build.VERSION.SDK_INT >= 17) {
			context.sendBroadcastAsUser(intent, UserHandleHelper.getCurrentUser());
			
		} else {
			context.sendBroadcast(intent);
		}
	}
}
