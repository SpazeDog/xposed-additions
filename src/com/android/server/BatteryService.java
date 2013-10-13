package com.android.server;

import android.os.Binder;

public class BatteryService extends Binder {
	public final boolean isPowered() { return false; }
	public final boolean isPowered(int plugTypeSet) { return false; }
	public final int getPlugType() { return 0; }
}
