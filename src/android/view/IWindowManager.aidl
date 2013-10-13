package android.view;

import android.view.InputEvent;

/** @hide */
interface IWindowManager {
	boolean injectInputEventNoWait(in InputEvent ev);
}