package com.spazedog.xposed.additionsgb.hooks;

import android.annotation.SuppressLint;
import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.IWindowManager;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.android.internal.policy.impl.RecentApplicationsDialog;
import com.android.internal.statusbar.IStatusBarService;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.hooks.tools.XC_ClassHook;

import de.robv.android.xposed.XSharedPreferences;

public final class PhoneWindowManagerHook extends XC_ClassHook {
	
	protected final Runnable mMappingRunnable = new Runnable() {
        @Override
        public void run() {
        	Boolean longpress = mKeyPressed != 0;
        	String action = longpress ? mKeyPressAction : mKeyClickAction;
        	
        	if (action.equals("disabled")) {
        		// Disable the button

        	} else if (action.startsWith("btn_")) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Executing key map '" + action + "'");
				}
				
				triggerKeyEvent( getKeyCode(action) );
        		
        	} else if (action.equals("poweron")) {
        		/*
        		 * Using triggerKeyEvent() while the device is sleeping
        		 * does not work on all devices. So we add this forced wakeup feature
        		 * for these devices. 
        		 */
        		
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Executing key map 'poweron'");
				}

        		mWakeLock.acquire();
        		
        		/*
        		 * WakeLock.acquire(timeout) causes Gingerbread to produce
        		 * soft reboots when trying to manually release them.
        		 * So we make our own timeout feature to avoid this.
        		 */
        		mHandler.postDelayed(mWakelockRunnable, 10000);
        		
        	} else if (action.equals("recentapps")) {
        		recentAppsDialog();
        		
        	} else if (action.equals("powermenu")) {
        		globalActionsDialog();
        		
        	} else {
        		if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Executing default key action");
				}
        		
        		triggerKeyEvent(mKeyLast);
        	}
        }
    };
    
    protected final Runnable mLongpressRunnable = new Runnable() {
        @Override
        public void run() {
        	Integer delay = mKeyDelay;
        	
			while (mKeyPressed != 0 && delay > 0) {
				try {
					Thread.sleep(10);
					
					delay -= 10;
					
				} catch (InterruptedException e) {}
			}
			
			mMappingRunnable.run();
			
			try {
				/*
				 * Give some time before releasing the partial wakelock
				 */
				Thread.sleep(300);
				
			} catch (InterruptedException e) {}
			
			if (mWakeLockPartial.isHeld()) {
				mWakeLockPartial.release();
			}
        }
    };
    
    protected final Runnable mWakelockRunnable = new Runnable() {
        @Override
        public void run() {
        	if (mWakeLock.isHeld()) {
        		mWakeLock.release();
        	}
        }
    };
	
    public final static int ACTION_PASS_TO_USER = 0x00000001;
    public final static int FLAG_INJECTED = 0x01000000;
	
    protected Context mContext;
    protected PowerManager mPowerManager;
    protected WakeLock mWakeLock;
    protected WakeLock mWakeLockPartial;
    protected Handler mHandler;
    protected IWindowManager mWindowManager;
    
    protected RecentApplicationsDialog mRecentApplicationsDialog;
    protected IStatusBarService mStatusBarService;
    
    protected Boolean mKeyCancelDefault = false;
    protected Integer mKeyPressed = 0;
    protected Integer mKeyLast = 0;
    protected Integer mKeyDelay;
    protected String mKeyClickAction;
    protected String mKeyPressAction;
    
    protected Boolean mScreenIsOn = false;
    protected Boolean mScreenWasOn = false;

	public PhoneWindowManagerHook(String className, ClassLoader classLoader) {
		super(className, classLoader);
	}
	
	/**
	 * Gingerbread uses arguments init(Context, IWindowManager, LocalPowerManager)
	 * ICS uses arguments init(Context, IWindowManager, WindowManagerFuncs, LocalPowerManager)
	 * JellyBean uses arguments init(Context, IWindowManager, WindowManagerFuncs)
	 */
	@SuppressWarnings("deprecation")
	public void xa_init(final MethodHookParam param) {
		if (Common.DEBUG) {
			Log.d(Common.PACKAGE_NAME, "Running PhoneWindowManager init() method");
		}
    	
    	mContext = (Context) param.args[0];
    	mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    	mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE | PowerManager.ACQUIRE_CAUSES_WAKEUP, "PhoneWindowManagerHook");
    	mWakeLockPartial = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneWindowManagerHookPartial");
    	mHandler = new Handler();
    	mWindowManager = (IWindowManager) param.args[1];
	}
	
	/**
	 * Gingerbread uses arguments interceptKeyBeforeQueueing(Long whenNanos, Integer action, Integer flags, Integer keyCode, Integer scanCode, Integer policyFlags, Boolean isScreenOn)
	 * ICS/JellyBean uses arguments interceptKeyBeforeQueueing(KeyEvent event, Integer policyFlags, Boolean isScreenOn)
	 */
	@SuppressLint("Wakelock")
	public void xb_interceptKeyBeforeQueueing(final MethodHookParam param) {
		final int action = (Integer) (SDK_GB ? param.args[1] : ((KeyEvent) param.args[0]).getAction());
		final int policyFlags = (Integer) (SDK_GB ? param.args[5] : param.args[1]);
		final int keyCode = (Integer) (SDK_GB ? param.args[3] : ((KeyEvent) param.args[0]).getKeyCode());
		final boolean isScreenOn = (Boolean) (SDK_GB ? param.args[6] : param.args[2]);
		
		final boolean down = action == KeyEvent.ACTION_DOWN;
		final boolean isInjected = (policyFlags & FLAG_INJECTED) != 0;
		
		/*
		 * If you leave out ACTION_PASS_TO_USER, Gingerbread will not call interceptKeyBeforeDispatching.
		 * And if you leave out '0' or pass something like '-1', Gingerbread will turn off the screen.
		 */
		int result = 0 | ACTION_PASS_TO_USER;
		
		/*
		 * Do not handle injected events.
		 * We might have triggered these ourself.
		 */
		if (!isInjected) {
			mScreenIsOn = isScreenOn;
			
			if (down) {
				mScreenWasOn = isScreenOn;
				mKeyCancelDefault = false;
				
				if (mWakeLock.isHeld()) {
					mWakeLock.release();
					mHandler.removeCallbacks(mWakelockRunnable);
				}
				
				/*
				 * If the screen was turned of by a on-the-screen button (Hardware-like button), 
				 * interceptKeyBeforeQueueing is not called on keyup and so mKeyPressed will remain 'true'
				 */
				if (mKeyPressed != 0 && !isScreenOn) {
					mKeyPressed = 0;
				}
			}
			
			if (!down && mKeyPressed != 0) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Key event is currently locked. Unlocking and releasing keyup event");
				}
				
				mKeyPressed = 0;
				
				param.setResult(0);
			
			} else {
				if (down && mKeyPressed == 0) {
					SharedPreferences preferences = new XSharedPreferences(Common.PACKAGE_NAME, Common.HOOK_PREFERENCES);
					String button = getMappedKey(keyCode);
					
					if (preferences.getBoolean(button + "_mapped", false)) {
						if (Common.DEBUG) {
							Log.d(Common.PACKAGE_NAME, "Found mapping for the key event " + button);
						}
						
						mKeyClickAction = preferences.getString(button + "_action_click", button);
						mKeyPressAction = preferences.getString(button + "_action_press", "disabled");
						mKeyDelay = Integer.parseInt(preferences.getString("btn_longpress_delay", "500"));
						mKeyPressed = mKeyLast = keyCode;
						
						/* 
						 * interceptKeyBeforeDispatching is not called while the screen is off.
						 * So instead we use a handler to do this job.
						 */
						if (!isScreenOn) {
							if (Common.DEBUG) {
								Log.d(Common.PACKAGE_NAME, "Handling screen off event");
							}
							
							mKeyCancelDefault = true;
							
							/*
							 * Some devices refuses to run the handler while in deep sleep.
							 * So we have to acquire a partial wakelock (start the CPU) to ensure
							 * that our request get's handled. 
							 */
							mWakeLockPartial.acquire();
							
							/*
							 * Key events are not dispatched during sleep, so
							 * we have to go another way in this state. 
							 */
							mHandler.post(mLongpressRunnable);
						}
						
						param.setResult(result);
						
					} else {
						if (Common.DEBUG) {
							Log.d(Common.PACKAGE_NAME, "No mapping available for the key event " + button);
						}
						
						mKeyCancelDefault = false;
					}
					
				} else if (down && mKeyPressed != 0) {
					param.setResult(0);
				}
			}
			
		} else {
			if (mKeyCancelDefault) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Removing Injected flag before Queueing");
				}
				
				int pos = SDK_GB ? 5 : 1;
				
				/*
				 * Remove the Injected policy before sending this to the original method
				 */
				param.args[pos] = policyFlags^FLAG_INJECTED;
			}
		}
	}
	
	/**
	 * Gingerbread uses arguments interceptKeyBeforeDispatching(WindowState win, Integer action, Integer flags, Integer keyCode, Integer scanCode, Integer metaState, Integer repeatCount, Integer policyFlags)
	 * ICS/JellyBean uses arguments interceptKeyBeforeDispatching(WindowState win, KeyEvent event, Integer policyFlags)
	 */
	public void xb_interceptKeyBeforeDispatching(final MethodHookParam param) {
		final int action = (Integer) (SDK_GB ? param.args[1] : ((KeyEvent) param.args[1]).getAction());
		final int policyFlags = (Integer) (SDK_GB ? param.args[7] : param.args[2]);
		
		final boolean down = (action == KeyEvent.ACTION_DOWN);
		final boolean isInjected = (policyFlags & FLAG_INJECTED) != 0;
		
		if (!isInjected) {
			if (mKeyCancelDefault) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Default actions is currentl disabled, skipping dispatching");
				}
				
				param.setResult(SDK_GB ? true : -1);
				
			} else if (down && mKeyPressed != 0) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Checking current event type");
				}
				
				Integer delay = mKeyDelay;
				
				mKeyCancelDefault = true;
				
				while (mKeyPressed != 0 && delay > 0) {
					try {
						Thread.sleep(10);
						
						delay -= 10;
						
					} catch (InterruptedException e) {}
				}
				
				if (Common.DEBUG) {
					if (mKeyPressed != 0) {
						Log.d(Common.PACKAGE_NAME, "Handling long press event");
						
					} else {
						Log.d(Common.PACKAGE_NAME, "Handling click event");
					}
				}
				
				mHandler.post(mMappingRunnable);
				
				param.setResult(SDK_GB ? true : -1);
			}
			
		} else {
			if (mKeyCancelDefault) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Removing Injected flag before Dispatching");
				}
				
				int pos = SDK_GB ? 7 : 2;
				
				/*
				 * Remove the Injected policy before sending this to the original method
				 */
				param.args[pos] = policyFlags^FLAG_INJECTED;
			}
		}
	}
	
	/**
	 * Jellybean uses a new InputManager service to handle injected input events, while
	 * ICS and older has this implemented into the WindowManager service.
	 */
	@SuppressLint("InlinedApi")
	protected void triggerKeyEvent(final int keyCode) {
		KeyEvent downEvent = null;
		
		if (!SDK_GB) {
			long now = SystemClock.uptimeMillis();
			
	        downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
	        		keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
	                	KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);

		} else {
			downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
		}
		
		KeyEvent upEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP);
		
		if (SDK_JB) {
			InputManager im = InputManager.getInstance();

			im.injectInputEvent(downEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
			im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
			
		} else {
	        try {
	            mWindowManager.injectInputEventNoWait(downEvent);
	            mWindowManager.injectInputEventNoWait(upEvent);
	            
	        } catch (RemoteException e) {
	            e.printStackTrace();
	        }
		}
	}
	
	protected void recentAppsDialog() {
		sendCloseSystemWindows("recentapps");
		
		if (!SDK_GB) {
			if (mStatusBarService == null) {
				mStatusBarService = IStatusBarService.Stub.asInterface( ServiceManager.getService("statusbar"));
			}
			
			if (mStatusBarService != null) {
				try {
					mStatusBarService.toggleRecentApps();
					
				} catch (RemoteException e) {
					mStatusBarService = null;
				}
			}
			
		} else {
	        if (mRecentApplicationsDialog == null) {
	        	mRecentApplicationsDialog = new RecentApplicationsDialog(mContext);
	        }
	        
	        mRecentApplicationsDialog.show();
		}
	}
	
	protected void globalActionsDialog() {
		/*
		 * TODO: Make our own version 
		 */
		invokeMethod("showGlobalActionsDialog");
	}
	
	protected void sendCloseSystemWindows(String reason) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(reason);
                
            } catch (RemoteException e) {}
        }
    }
	
	protected Integer getKeyCode(String map) {		
		if (map.endsWith("_power")) return KeyEvent.KEYCODE_POWER;
		else if (map.endsWith("_home")) return KeyEvent.KEYCODE_HOME;
		else if (map.endsWith("_menu")) return KeyEvent.KEYCODE_MENU;
		else if (map.endsWith("_back")) return KeyEvent.KEYCODE_BACK;
		else if (map.endsWith("_search")) return KeyEvent.KEYCODE_SEARCH;
		else if (map.endsWith("_volup")) return KeyEvent.KEYCODE_VOLUME_UP;
		else if (map.endsWith("_voldown")) return KeyEvent.KEYCODE_VOLUME_DOWN;
		
		return null;
	}
	
	protected String getMappedKey(Integer keyCode) {
		if (mScreenWasOn) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_POWER: return "btn_on_power";
				case KeyEvent.KEYCODE_HOME: return "btn_on_home";
				case KeyEvent.KEYCODE_MENU: return "btn_on_menu";
				case KeyEvent.KEYCODE_BACK: return "btn_on_back";
				case KeyEvent.KEYCODE_SEARCH: return "btn_on_search";
				case KeyEvent.KEYCODE_VOLUME_UP: return "btn_on_volup";
				case KeyEvent.KEYCODE_VOLUME_DOWN: return "btn_on_voldown";
			}
			
		} else {
			switch (keyCode) {
				case KeyEvent.KEYCODE_POWER: return "btn_off_power";
				case KeyEvent.KEYCODE_HOME: return "btn_off_home";
				case KeyEvent.KEYCODE_MENU: return "btn_off_menu";
				case KeyEvent.KEYCODE_BACK: return "btn_off_back";
				case KeyEvent.KEYCODE_SEARCH: return "btn_off_search";
				case KeyEvent.KEYCODE_VOLUME_UP: return "btn_off_volup";
				case KeyEvent.KEYCODE_VOLUME_DOWN: return "btn_off_voldown";
			}
		}
		
		return null;
	}
}
