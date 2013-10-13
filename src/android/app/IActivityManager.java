package android.app;

import android.os.RemoteException;

public interface IActivityManager {
	public void closeSystemDialogs(String reason) throws RemoteException;
}
