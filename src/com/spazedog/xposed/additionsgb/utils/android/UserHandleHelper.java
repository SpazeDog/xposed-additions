package com.spazedog.xposed.additionsgb.utils.android;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectConstructor;
import com.spazedog.lib.reflecttools.ReflectField;
import com.spazedog.lib.reflecttools.utils.ReflectConstants.Match;

import android.os.UserHandle;

public class UserHandleHelper {
	public static UserHandle getCurrentUser() {
		ReflectClass userHandleClass = ReflectClass.forName("android.os.UserHandle");
		
		/*
		 * TODO: This some times returns the default -2 as the userid and we get an error about cross-communication. 
		 * We need to build a proper way to detect current users. At the same time maybe add real multi-user support 
		 * with support for multi-configurations based on current user.
		 */
		
		if (userHandleClass != null) {
			ReflectConstructor userHandleConstructor = userHandleClass.findConstructor(Match.BEST, Integer.TYPE);
			ReflectField currentUserField = userHandleClass.findField("USER_CURRENT");
			int userid = (Integer) currentUserField.getValue();
			
			if (userid < 0) {
				userid = 0;
			}
			
			if (userHandleConstructor != null && currentUserField != null) {
				return (UserHandle) userHandleConstructor.invoke( userid );
			}
		}
		
		return null;
	}
}
