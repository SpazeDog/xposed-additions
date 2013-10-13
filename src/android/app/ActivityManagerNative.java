package android.app;

import android.os.Binder;

public abstract class ActivityManagerNative extends Binder implements IActivityManager {
	static public IActivityManager getDefault() { return null; }
	static public boolean isSystemReady() { return false; }
}
