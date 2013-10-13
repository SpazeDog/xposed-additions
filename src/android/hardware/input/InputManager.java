package android.hardware.input;

import android.view.InputEvent;

public final class InputManager {
	public static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
	
	public static InputManager getInstance() { return null; }
	public boolean injectInputEvent(InputEvent event, int mode) { return false; }
}
