package com.spazedog.xposed.additionsgb.utils.android;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectConstructor;
import com.spazedog.lib.reflecttools.ReflectField;
import com.spazedog.lib.reflecttools.utils.ReflectConstants.Match;

import android.os.UserHandle;

public class UserHandleHelper {
	public static UserHandle getCurrentUser() {
		ReflectClass userHandleClass = ReflectClass.forName("android.os.UserHandle");
		
		if (userHandleClass != null) {
			ReflectConstructor userHandleConstructor = userHandleClass.findConstructor(Match.BEST, Integer.TYPE);
			ReflectField currentUserField = userHandleClass.findField("USER_CURRENT");
			
			if (userHandleConstructor != null && currentUserField != null) {
				return (UserHandle) userHandleConstructor.invoke( currentUserField.getValue() );
			}
		}
		
		return null;
	}
}
