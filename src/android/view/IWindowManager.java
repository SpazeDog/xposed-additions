package android.view;

import android.os.RemoteException;

public interface IWindowManager {
	public boolean injectKeyEvent(KeyEvent ev, boolean sync) throws RemoteException;
}
