package com.spazedog.xposed.additionsgb.utils.android;

import java.io.InputStream;
import java.util.HashMap;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectMethod;
import com.spazedog.lib.reflecttools.utils.ReflectConstants.Match;

public class XmlUtilsHelper {
	@SuppressWarnings("unchecked")
	public static final HashMap<String, ?> readMapXml(InputStream in) {
		ReflectClass xmlUtilsClass = ReflectClass.forName("com.android.internal.util.XmlUtils");
		
		if (xmlUtilsClass != null) {
			ReflectMethod readXmlMethod = xmlUtilsClass.findMethod("readMapXml", Match.BEST, InputStream.class);
			
			if (readXmlMethod != null) {
				return (HashMap<String, ?>) readXmlMethod.invoke(in);
			}
		}
		
		return null;
	}
}
