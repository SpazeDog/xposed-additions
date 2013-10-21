package com.android.internal.statusbar;

import android.os.IBinder;

public class IStatusBarService {
	/*
	 * You can't call Stub from Xposed it would seam. 
	 * Properly because it does not technically exist. 
	 * This little trick will do the job.
	 */
	public static class Stub {
		public static IStatusBarService asInterface(IBinder service) { return null; }
	}
}
