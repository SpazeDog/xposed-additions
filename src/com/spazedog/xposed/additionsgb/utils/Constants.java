package com.spazedog.xposed.additionsgb.utils;

public final class Constants {
	
	/*
	 * Let's start replacing the Common class with the utils package
	 */
	
	public static final class Intent {
		/*
		 * Making our own replacement for Android's onBoot broadcast, we can get the app triggered
		 * much sooner so that configs are restored earlier. It also removes the chance of things like XPrivacy or other similar 
		 * apps causing problems due to blocking of this broadcast. 
		 * 
		 * Also Android's shutdown broadcast is not always triggered and then there are devices like HTC 
		 * that has multiple versions of their own. 
		 * 
		 */
		public static final String ACTION_XSERVICE_READY = "com.spazedog.xposed.additionsgb.ACTION_XSERVICE_READY";
		public static final String ACTION_XSERVICE_SHUTDOWN = "com.spazedog.xposed.additionsgb.ACTION_XSERVICE_SHUTDOWN";
	}
}
