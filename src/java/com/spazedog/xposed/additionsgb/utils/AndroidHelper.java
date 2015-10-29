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
 
package com.spazedog.xposed.additionsgb.utils;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;

public class AndroidHelper {

	public static ReflectClass getUserHandle(int userid) {
		try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Object userhandle = ReflectClass.fromName("android.os.UserHandle").invokeConstructor(userid);

                return ReflectClass.fromReceiver(userhandle);
            }
			
		} catch (ReflectException e) {}

        return null;
	}

	public static boolean bindService(Context context, Intent intent, ServiceConnection conn, int flags, int userid) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			try {
                ReflectClass handle = getUserHandle(userid);
				
				if (handle != null) {
                    return (Boolean) ReflectClass.fromReceiver(context).invokeMethod("bindServiceAsUser", intent, conn, Context.BIND_AUTO_CREATE, handle);
				}
				
			} catch (ReflectException e) {}
		}
		
		return context.bindService(intent, conn, Context.BIND_AUTO_CREATE);
	}
}
