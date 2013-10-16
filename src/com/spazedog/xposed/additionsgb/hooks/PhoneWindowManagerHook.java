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
    
	public static final int KEY_DOWN = 8;
	public static final int KEY_CANCEL = 32;
	public static final int KEY_REPEAT = 128;
	public static final int KEY_ONGOING = 512;
	public static final int KEY_INJECTED = 2048;
	
	protected int mKeyPressDelay = 0;
	protected int mKeyDoubleDelay = 0;
	protected int mKeyFlags = 0;
	protected int mKeyCode = 0;
	protected String mKeyName;
	protected String[] mKeyActions;
	
	protected Boolean mScreenWasOn;
	
	protected final Runnable mMappingRunnable = new Runnable() {
        @Override
        public void run() {
        	String action = (mKeyFlags & KEY_REPEAT) != 0 ? 
        			mKeyActions[1] : (mKeyFlags & KEY_DOWN) != 0 ? 
        					mKeyActions[2] : mKeyActions[0];
        					
        	mKeyFlags = KEY_CANCEL;
        					
			if (action.equals("disabled")) {
				// Disable the button
				
			} else if (action.startsWith("btn_")) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Invoking Re-map key '" + action + "'");
				}
				
				mKeyFlags |= KEY_INJECTED;
				
				triggerKeyEvent( getKeyCode(action) );
				
			} else if (action.equals("poweron")) { 
        		/*
        		 * Using triggerKeyEvent() while the device is sleeping
        		 * does not work on all devices. So we add this forced wakeup feature
        		 * for these devices. 
        		 */
				
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Invoking key action 'poweron'");
				}

        		mWakeLock.acquire();
        		
        		/*
        		 * WakeLock.acquire(timeout) causes Gingerbread to produce
        		 * soft reboots when trying to manually release them.
        		 * So we make our own timeout feature to avoid this.
        		 */
        		mHandler.postDelayed(mReleaseWakelock, 3000);
			
        	} else if (action.equals("poweroff")) { 
        		/*
        		 * Parsing the power code does not always work on Gingerbread.
        		 * So like poweron, we also make a poweroff to force the device off.
        		 */
        		
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Invoking key action 'poweroff'");
				}
        		
        		mPowerManager.goToSleep(SystemClock.uptimeMillis());
			
        	} else if (action.equals("recentapps")) {
        		recentAppsDialog();
        		
        	} else if (action.equals("powermenu")) {
        		globalActionsDialog();
        		
        	} else {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Invoking default key code '" + mKeyCode + "'");
				}
				
				mKeyFlags |= KEY_INJECTED;
				
				triggerKeyEvent(mKeyCode);
			}
			
			if (mWakeLockPartial.isHeld()) {
				mHandler.postDelayed(mReleasePartialWakelock, 100);
			}
        }
	};
	
	protected final Runnable mReleasePartialWakelock = new Runnable() {
        @Override
        public void run() {
        	if (mWakeLockPartial.isHeld()) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Releasing partial Wakelock");
				}
				
        		mWakeLockPartial.release();
        	}
        }
	};
	
    protected final Runnable mReleaseWakelock = new Runnable() {
        @Override
        public void run() {
        	if (mWakeLock.isHeld()) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Releasing poweron Wakelock");
				}
				
        		mWakeLock.release();
        	}
        }
    };

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
		
		if (!isInjected || (mKeyFlags & KEY_INJECTED) == 0) {
			if ((mKeyFlags & KEY_ONGOING) != 0) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Removing Re-map handler");
				}
				
				mHandler.removeCallbacks(mMappingRunnable);
			}
			
			if (down) {
				/*
				 * If the screen was turned of by a on-the-screen button (Hardware-like button), 
				 * interceptKeyBeforeQueueing is not called on keyup.
				 */
				if ((mKeyFlags & KEY_CANCEL) != 0 && !isScreenOn) {
					mKeyFlags = 0;
				}
				
				if ((mKeyFlags & KEY_ONGOING) == 0 || (mKeyFlags & KEY_REPEAT) != 0) {
		        	if (mWakeLock.isHeld()) {
						if (Common.DEBUG) {
							Log.d(Common.PACKAGE_NAME, "Releasing poweron Wakelock");
						}
						
						mHandler.removeCallbacks(mReleaseWakelock);
		        		mWakeLock.release();
		        	}
		        	
					if (Common.DEBUG) {
						Log.d(Common.PACKAGE_NAME, "Checking Re-map settings for the key code '" + keyCode + "'");
					}
					
					SharedPreferences preferences = new XSharedPreferences(Common.PACKAGE_NAME, Common.HOOK_PREFERENCES);
					mScreenWasOn = isScreenOn;
					mKeyName = getMappedKey(keyCode);
					
					if (mKeyName != null && preferences.getBoolean(mKeyName + "_mapped", false)) {
						if (Common.DEBUG) {
							Log.d(Common.PACKAGE_NAME, "Found Re-map for the key code '" + keyCode + " (" + mKeyName + ")'");
						}
						
						mKeyPressDelay = Integer.parseInt(preferences.getString("btn_longpress_delay", "500"));
						mKeyDoubleDelay = Integer.parseInt(preferences.getString("btn_doubleclick_delay", "100"));
						mKeyFlags = KEY_DOWN|KEY_ONGOING;
						mKeyCode = keyCode;
						mKeyActions = new String[]{
							preferences.getString(mKeyName + "_action_click", mKeyName),
							preferences.getString(mKeyName + "_action_double", "disabled"),
							preferences.getString(mKeyName + "_action_press", "disabled"),
						};
						
						if (!isScreenOn) {
							if (Common.DEBUG) {
								Log.d(Common.PACKAGE_NAME, "Acquiring a partial Wakelock");
							}
							
							mWakeLockPartial.acquire();
						}
						
						if (!mKeyActions[2].equals("disabled")) {
							if (Common.DEBUG) {
								Log.d(Common.PACKAGE_NAME, "Posting delayed long press handler");
							}
							
							mHandler.postDelayed(mMappingRunnable, mKeyPressDelay);
						}
						
						param.setResult(0);
						
					} else {
						if (Common.DEBUG) {
							Log.d(Common.PACKAGE_NAME, "No Re-map found for the key code '" + keyCode + "'");
						}
						
						mKeyFlags = 0;
					}
					
				} else if (mKeyCode == keyCode && (mKeyFlags & KEY_CANCEL) == 0) {
					mKeyFlags |= KEY_REPEAT|KEY_DOWN;
					
					if (Common.DEBUG) {
						Log.d(Common.PACKAGE_NAME, "Posting double click handler");
					}
					
					mHandler.post(mMappingRunnable);
					
					param.setResult(0);
					
				} else {
					param.setResult(0);
				}
				
			} else {
				if ((mKeyFlags & KEY_ONGOING) != 0 && mKeyCode == keyCode) {
					mKeyFlags ^= KEY_DOWN;
					
					if (!mKeyActions[1].equals("disabled")) {
						if (Common.DEBUG) {
							Log.d(Common.PACKAGE_NAME, "Posting delayed click handler");
						}
						
						mHandler.postDelayed(mMappingRunnable, mKeyDoubleDelay);
						
					} else {
						if (Common.DEBUG) {
							Log.d(Common.PACKAGE_NAME, "Posting click handler");
						}

						mHandler.post(mMappingRunnable);
					}
					
					param.setResult(0);
					
				} else if (((mKeyFlags & KEY_ONGOING) != 0 && mKeyCode != keyCode)) {
					param.setResult(0);
				}
			}
			
		} else {
			if (!down) {
				if (Common.DEBUG) {
					Log.d(Common.PACKAGE_NAME, "Resetting all flags");
				}
				
				mKeyFlags = 0;
			}
			
			if (Common.DEBUG) {
				Log.d(Common.PACKAGE_NAME, "Parsing injected key code '" + keyCode + "' to the system");
			}

			/*
			 * Remove the Injected policy before sending this to the original method
			 */
			param.args[ (SDK_GB ? 5 : 1) ] = policyFlags^FLAG_INJECTED;
		}
	}
	
	/**
	 * Gingerbread uses arguments interceptKeyBeforeDispatching(WindowState win, Integer action, Integer flags, Integer keyCode, Integer scanCode, Integer metaState, Integer repeatCount, Integer policyFlags)
	 * ICS/JellyBean uses arguments interceptKeyBeforeDispatching(WindowState win, KeyEvent event, Integer policyFlags)
	 */
	public void xb_interceptKeyBeforeDispatching(final MethodHookParam param) {
		if ((mKeyFlags & KEY_INJECTED) == 0 && ((mKeyFlags & KEY_ONGOING) != 0 || (mKeyFlags & KEY_CANCEL) != 0)) {
			if (Common.DEBUG) {
				Log.d(Common.PACKAGE_NAME, "Canceling Dispatching");
			}
			
			param.setResult(SDK_GB ? true : -1);
			
		} else {
			/*
			 * Remove the Injected policy before sending this to the original method
			 */
			final int policyFlags = (Integer) (SDK_GB ? param.args[7] : param.args[2]);
			
			param.args[ (SDK_GB ? 7 : 2) ] = policyFlags^FLAG_INJECTED;
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
		else if (map.endsWith("_play")) return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
		else if (map.endsWith("_previous")) return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
		else if (map.endsWith("_next")) return KeyEvent.KEYCODE_MEDIA_NEXT;
		
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
				case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return "btn_on_play";
				case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return "btn_on_previous";
				case KeyEvent.KEYCODE_MEDIA_NEXT: return "btn_on_next";
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
				case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return "btn_off_play";
				case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return "btn_off_previous";
				case KeyEvent.KEYCODE_MEDIA_NEXT: return "btn_off_next";
			}
		}
		
		return null;
	}
}
