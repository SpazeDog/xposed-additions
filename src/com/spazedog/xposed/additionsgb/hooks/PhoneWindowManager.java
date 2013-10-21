package com.spazedog.xposed.additionsgb.hooks;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Surface;
import android.widget.Toast;

import com.android.internal.statusbar.IStatusBarService;
import com.spazedog.xposed.additionsgb.Common;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class PhoneWindowManager extends XC_MethodHook {
	
	private PhoneWindowManager() {}
	
	public static void inject() {
		Class<?> phoneWindowManager = XposedHelpers.findClass("com.android.internal.policy.impl.PhoneWindowManager", null);
		
		PhoneWindowManager keyInjector = new PhoneWindowManager();
		
		XposedBridge.hookAllConstructors(phoneWindowManager, keyInjector);
		XposedBridge.hookAllMethods(phoneWindowManager, "init", keyInjector);
		XposedBridge.hookAllMethods(phoneWindowManager, "interceptKeyBeforeQueueing", keyInjector);
		XposedBridge.hookAllMethods(phoneWindowManager, "interceptKeyBeforeDispatching", keyInjector);
	}
	
	@Override
	protected final void beforeHookedMethod(final MethodHookParam param) {
		if (param.method instanceof Method) {
			if (((Method) param.method).getName().equals("interceptKeyBeforeQueueing")) {
				hook_interceptKeyBeforeQueueing(param);
				
			} else if (((Method) param.method).getName().equals("interceptKeyBeforeDispatching")) {
				hook_interceptKeyBeforeDispatching(param);
			}
			
		} else {
			hook_construct(param);
		}
	}
	
	@Override
	protected final void afterHookedMethod(final MethodHookParam param) {
		if (param.method instanceof Method) {
			if (((Method) param.method).getName().equals("init")) {
				hook_init(param);
			}
		}
	}
	
	private int FLAG_INJECTED;
	private int FLAG_VIRTUAL;
	private int FLAG_PASS_TO_USER;
	private int INJECT_INPUT_EVENT_MODE_ASYNC;
	
	private static final int SDK_NUMBER = android.os.Build.VERSION.SDK_INT;
	
	private WeakReference<Object> mHookedReference;
	
	private Context mContext;
	private Object mWindowManager;
	private PowerManager mPowerManager;
	private WakeLock mWakeLock;
	private WakeLock mWakeLockPartial;
	private Handler mHandler;
	
	private Object mRecentAppsTrigger;
	
	private Class<?> mActivityManagerNativeClass;
	private Class<?> mWindowManagerPolicyClass; 
	private Class<?> mServiceManagerClass;
	private Class<?> mWindowStateClass;
	private Class<?> mInputManagerClass;
	
	public static final int KEY_DOWN = 8;
	public static final int KEY_CANCEL = 32;
	public static final int KEY_RESET = 64;
	public static final int KEY_REPEAT = 128;
	public static final int KEY_ONGOING = 512;
	public static final int KEY_APPLICATION = 1024;
	public static final int KEY_INJECTED = 2048;
	
	protected int mKeyPressDelay = 0;
	protected int mKeyTapDelay = 0;
	protected int mKeyFlags = 0;
	protected int mKeyCode = 0;
	protected String[] mKeyActions;
	
	protected Boolean mScreenWasOn;
	
	protected Boolean mInterceptKeycode = false;
	
	protected final Runnable mMappingRunnable = new Runnable() {
        @Override
        public void run() {
        	String action = (mKeyFlags & KEY_REPEAT) != 0 ? 
        			mKeyActions[1] : (mKeyFlags & KEY_DOWN) != 0 ? 
        					mKeyActions[2] : mKeyActions[0];
        					
        	if ((mKeyFlags & KEY_REPEAT) != 0 || (mKeyFlags & KEY_DOWN) != 0) {
        		performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        	}
        					
        	mKeyFlags = KEY_CANCEL|KEY_RESET;
        	
        	if (action.equals("default")) {
        		action = "" + mKeyCode;
        	}
        	
        	if (action.equals("disabled")) {
        		// Disable the button
        		
        	} else if (action.equals("poweron")) { 
        		/*
        		 * Using triggerKeyEvent() while the device is sleeping
        		 * does not work on all devices. So we add this forced wakeup feature
        		 * for these devices. 
        		 */
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
        		mPowerManager.goToSleep(SystemClock.uptimeMillis());
        		
        	} else if (action.equals("recentapps")) {
        		openRecentAppsDialog();
        		
        	} else if (action.equals("powermenu")) {
        		openGlobalActionsDialog();
        		
        	} else if (action.equals("flipleft")) {
        		rotateLeft();
        		
        	} else if (action.equals("flipright")) {
        		rotateRight();
        		
        	} else if (action.equals("fliptoggle")) {
        		toggleRotation();
        		
        	} else {
        		Integer keyCode = Integer.parseInt(action);
        		
				mKeyFlags |= KEY_INJECTED;
				
				triggerKeyEvent(keyCode);
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
        		mWakeLockPartial.release();
        	}
        }
	};
	
    protected final Runnable mReleaseWakelock = new Runnable() {
        @Override
        public void run() {
        	if (mWakeLock.isHeld()) {
        		mWakeLock.release();
        	}
        }
    };
	
	private void hook_construct(final MethodHookParam param) {
		log(1, "construct");
		
		mHookedReference = new WeakReference<Object>(param.thisObject);
		
		mWindowManagerPolicyClass = XposedHelpers.findClass("android.view.WindowManagerPolicy", null);
		mActivityManagerNativeClass = XposedHelpers.findClass("android.app.ActivityManagerNative", null);
		mServiceManagerClass = XposedHelpers.findClass("android.os.ServiceManager", null);
		mWindowStateClass = XposedHelpers.findClass("android.view.WindowManagerPolicy$WindowState", null);
		
		if (SDK_NUMBER > 10) {
			mInputManagerClass = XposedHelpers.findClass("android.hardware.input.InputManager", null);
			INJECT_INPUT_EVENT_MODE_ASYNC = XposedHelpers.getStaticIntField(mInputManagerClass, "INJECT_INPUT_EVENT_MODE_ASYNC");
		}

		FLAG_INJECTED = XposedHelpers.getStaticIntField(mWindowManagerPolicyClass, "FLAG_INJECTED");
		FLAG_VIRTUAL = XposedHelpers.getStaticIntField(mWindowManagerPolicyClass, "FLAG_VIRTUAL");
		FLAG_PASS_TO_USER = XposedHelpers.getStaticIntField(mWindowManagerPolicyClass, "FLAG_PASS_TO_USER");
	}
	
	/**
	 * Gingerbread uses arguments init(Context, IWindowManager, LocalPowerManager)
	 * ICS uses arguments init(Context, IWindowManager, WindowManagerFuncs, LocalPowerManager)
	 * JellyBean uses arguments init(Context, IWindowManager, WindowManagerFuncs)
	 */
	private void hook_init(final MethodHookParam param) {
		log(1, "init");
		
    	mContext = (Context) param.args[0];
    	mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    	mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE | PowerManager.ACQUIRE_CAUSES_WAKEUP, "PhoneWindowManagerHook");
    	mWakeLockPartial = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneWindowManagerHookPartial");
    	mHandler = new Handler();
    	mWindowManager = param.args[1];
    	
    	mContext.registerReceiver(
    		new BroadcastReceiver() {
	    		@Override
	    		public void onReceive(Context context, Intent intent) {
	    			if (intent.getStringExtra("request").equals(Common.BroadcastOptions.REQUEST_ENABLE_KEYCODE_INTERCEPT)) {
	    				log(10, "on");
	    				
	    				mInterceptKeycode = true;
	    				
	    			} else if (intent.getStringExtra("request").equals(Common.BroadcastOptions.REQUEST_DISABLE_KEYCODE_INTERCEPT)) {
	    				log(10, "off");
	    				
	    				mInterceptKeycode = false;
	    				
	    			} else if (intent.getStringExtra("request").equals(Common.BroadcastOptions.REQUEST_RELOAD_CONFIGS)) {
	    				Common.loadSharedPreferences(null, true);
	    			}
	    		}
	    	}, 
	    	new IntentFilter(Common.BroadcastOptions.INTENT_ACTION_REQUEST), 
	    	Common.BroadcastOptions.PERMISSION_REQUEST, 
	    	null
	    );
	}
	
	/**
	 * Gingerbread uses arguments interceptKeyBeforeQueueing(Long whenNanos, Integer action, Integer flags, Integer keyCode, Integer scanCode, Integer policyFlags, Boolean isScreenOn)
	 * ICS/JellyBean uses arguments interceptKeyBeforeQueueing(KeyEvent event, Integer policyFlags, Boolean isScreenOn)
	 */
	private void hook_interceptKeyBeforeQueueing(final MethodHookParam param) {
		log(1, "interceptKeyBeforeQueueing");
		
		final int action = (Integer) (SDK_NUMBER <= 10 ? param.args[1] : ((KeyEvent) param.args[0]).getAction());
		final int policyFlags = (Integer) (SDK_NUMBER <= 10 ? param.args[5] : param.args[1]);
		final int keyCode = (Integer) (SDK_NUMBER <= 10 ? param.args[3] : ((KeyEvent) param.args[0]).getKeyCode());
		final boolean isScreenOn = (Boolean) (SDK_NUMBER <= 10 ? param.args[6] : param.args[2]);
		
		final boolean down = action == KeyEvent.ACTION_DOWN;
		final boolean isInjected = (policyFlags & FLAG_INJECTED) != 0;
		
		if (!isInjected) {
			if ((mKeyFlags & KEY_ONGOING) != 0) {
				log(5, "Re-map");
				
				mHandler.removeCallbacks(mMappingRunnable);
			}
			
			if (down && (mKeyFlags & KEY_RESET) != 0) {
				log(3);
				
				mKeyFlags = 0;
			}
			
			if (!mInterceptKeycode || !isScreenOn) {
				if (down) {
					if ((mKeyFlags & KEY_ONGOING) == 0 || (mKeyFlags & KEY_REPEAT) != 0) {
			        	if (mWakeLock.isHeld()) {
							log(7, "poweron");
							
							mHandler.removeCallbacks(mReleaseWakelock);
			        		mWakeLock.release();
			        	}
						
						mScreenWasOn = isScreenOn;
						
						if (Common.Remap.isKeyEnabled(keyCode, !isScreenOn)) {
							log(8, keyCode);
							
							if ((policyFlags & FLAG_VIRTUAL) != 0) {
								performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
							}
							
							mKeyPressDelay = Common.Remap.getPressDelay();
							mKeyTapDelay = Common.Remap.getTapDelay();
							mKeyFlags = KEY_DOWN|KEY_ONGOING;
							mKeyCode = keyCode;
							mKeyActions = new String[]{
								Common.Remap.getKeyClick(keyCode, !isScreenOn),
								Common.Remap.getKeyTap(keyCode, !isScreenOn),
								Common.Remap.getKeyPress(keyCode, !isScreenOn)
							};
							
							log(15, mKeyActions[0], mKeyActions[1], mKeyActions[2]);
							
							if (!isScreenOn) {
								log(6, "partial");
								
								mWakeLockPartial.acquire();
							}
							
							if (!mKeyActions[2].equals("disabled") && !mKeyActions[2].equals("default")) {
								log(4, "long press");
								
								mHandler.postDelayed(mMappingRunnable, mKeyPressDelay);
								
								param.setResult(0);
								
							} else if (mKeyActions[2].equals("default")) {
								log(14, "KEY_APPLICATION");
								
								mKeyFlags |= KEY_APPLICATION;
								
								param.setResult(0|FLAG_PASS_TO_USER);
								
							} else {
								param.setResult(0);
							}
							
						} else {
							log(12, keyCode);
							
							mKeyFlags = 0;
						}
						
					} else if (mKeyCode == keyCode && (mKeyFlags & KEY_CANCEL) == 0) {
						log(4, "double click");
						
						mKeyFlags |= KEY_REPEAT|KEY_DOWN;
						
						mHandler.post(mMappingRunnable);
						
						param.setResult(0);
						
					} else {
						param.setResult(0);
					}
					
				} else if ((mKeyFlags & KEY_RESET) == 0) {
					if ((mKeyFlags & KEY_ONGOING) != 0 && mKeyCode == keyCode) {
						mKeyFlags ^= KEY_DOWN;
						mKeyFlags ^= KEY_APPLICATION;
						
						if (!mKeyActions[1].equals("disabled")) {
							log(4, "delayed click");
							
							mHandler.postDelayed(mMappingRunnable, mKeyTapDelay);
							
						} else {
							log(4, "click");
	
							mHandler.post(mMappingRunnable);
						}
						
						param.setResult(0);
						
					} else if (((mKeyFlags & KEY_ONGOING) != 0 && mKeyCode != keyCode)) {
						param.setResult(0);
					}
					
				} else if ((mKeyFlags & KEY_CANCEL) != 0) {
					param.setResult(0);
					
				} else if (mKeyFlags != 0) {
					param.setResult(0|FLAG_PASS_TO_USER);
				}
				
			} else {
				if (down) {
					/*
					 * Send the keycode through the broadcast system
					 */
					log(11, keyCode);
					
					Intent intent = new Intent(Common.BroadcastOptions.INTENT_ACTION_RESPONSE);
					intent.putExtra("keycode", keyCode);
					
					mContext.sendBroadcast(intent);
				}
				
				param.setResult(0);
			}
			
		} else {
			log(2, keyCode);
			
			if ((mKeyFlags & KEY_INJECTED) == 0) {
				/*
				 * Remove the Injected policy before sending this to the original method.
				 */
				param.args[ (SDK_NUMBER <= 10 ? 5 : 1) ] = policyFlags^FLAG_INJECTED;
			}
		}
	}
	
	/**
	 * Gingerbread uses arguments interceptKeyBeforeDispatching(WindowState win, Integer action, Integer flags, Integer keyCode, Integer scanCode, Integer metaState, Integer repeatCount, Integer policyFlags)
	 * ICS/JellyBean uses arguments interceptKeyBeforeDispatching(WindowState win, KeyEvent event, Integer policyFlags)
	 */
	private void hook_interceptKeyBeforeDispatching(final MethodHookParam param) {
		log(1, "interceptKeyBeforeDispatching");
		
		final int action = (Integer) (SDK_NUMBER <= 10 ? param.args[1] : ((KeyEvent) param.args[1]).getAction());
		final int repeatCount = (Integer) (SDK_NUMBER <= 10 ? param.args[6] : ((KeyEvent) param.args[1]).getRepeatCount());
		
		final boolean down = action == KeyEvent.ACTION_DOWN;
		
		if (repeatCount == 0 || (mKeyFlags & KEY_APPLICATION) == 0) {
			if ((mKeyFlags & KEY_INJECTED) == 0 && ((mKeyFlags & KEY_ONGOING) != 0 || (mKeyFlags & KEY_CANCEL) != 0)) {
				log(9, "dispatch");
				
				param.setResult(SDK_NUMBER <= 10 ? true : -1);
				
			} else {
				if ((mKeyFlags & KEY_INJECTED) == 0) {
					/*
					 * Remove the Injected policy before sending this to the original method
					 */
					final int policyFlags = (Integer) (SDK_NUMBER <= 10 ? param.args[7] : param.args[2]);
					
					param.args[ (SDK_NUMBER <= 10 ? 7 : 2) ] = policyFlags^FLAG_INJECTED;
				}
			}
			
		} else if (down) {
			log(13, mKeyCode);
			
			/*
			 * If long press has been set to default, send the repeat events to the original Dispatcher
			 */
			mKeyFlags ^= KEY_ONGOING;
			mKeyFlags |= KEY_RESET;
			
			if (SDK_NUMBER <= 10) {
				param.args[6] = repeatCount - 1;
				
			} else {
				KeyEvent event = (KeyEvent) param.args[0];
				
				param.args[1] = KeyEvent.changeTimeRepeat(event, event.getEventTime(), (repeatCount - 1));
			}
		}
	}
	
	private void log(Integer msgId, Object... args) {
		if (Common.DEBUG) {
			String message = "";
			
			switch (msgId) {
				case 1: message = "Running method " + args[0] + "()"; break;
				case 2: message = "Parsing injected key code " + args[0] + " to the system"; break;
				case 3: message = "Resetting internal flags"; break;
				case 4: message = "Posting " + args[0] + " handler"; break;
				case 5: message = "Removing " + args[0] + " handler"; break;
				case 6: message = "Acquiring " + args[0] + " wakelock"; break;
				case 7: message = "Releasing " + args[0] + " wakelock"; break;
				case 8: message = "Found Re-map for the key code " + args[0] + ""; break;
				case 9: message = "Canceling " + args[0] + " operation"; break;
				case 10: message = "KeyCode intercept has been turned " + args[0] + ""; break;
				case 11: message = "Sending the KeyCode " + args[0] + " through the broadcast system"; break;
				case 12: message = "Skipping the KeyCode " + args[0] + ". Key is not enabled"; break;
				case 13: message = "Parsing key code " + args[0] + " to original dispatcher"; break;
				case 14: message = "Setting flag " + args[0]; break;
				case 15: message = "Using key config [" + args[0] + ", " + args[1] + ", " + args[2] + "]"; break;
			}
			
			Common.log(Common.PACKAGE_NAME + "$PhoneWindowManager", message);
		}
	}
	
	private void openRecentAppsDialog() {
		sendCloseSystemWindows("recentapps");
		
		if (SDK_NUMBER > 10) {
			if (mRecentAppsTrigger == null) {
				mRecentAppsTrigger = IStatusBarService.Stub.asInterface(
					(IBinder) XposedHelpers.callStaticMethod(
						mServiceManagerClass,
						"getService",
						new Class<?>[]{String.class},
						"statusbar"
					)
				);
			}
			
			XposedHelpers.callMethod(mRecentAppsTrigger, "toggleRecentApps");
			
		} else {
			if (mRecentAppsTrigger == null) {
				mRecentAppsTrigger = XposedHelpers.newInstance(
						XposedHelpers.findClass("com.android.internal.policy.impl.RecentApplicationsDialog", null),
						new Class<?>[]{Context.class},
						mContext
				);
			}
			
			XposedHelpers.callMethod(mRecentAppsTrigger, "show");
		}
	}
	
	private void openGlobalActionsDialog() {
		XposedHelpers.callMethod(mHookedReference.get(), "showGlobalActionsDialog");
	}
	
	private void performHapticFeedback(Integer effectId) {
		XposedHelpers.callMethod(
				mHookedReference.get(), 
				"performHapticFeedbackLw", 
				new Class<?>[]{mWindowStateClass, Integer.TYPE, Boolean.TYPE},
				null, effectId, false
		);
	}
	
	private void sendCloseSystemWindows(String reason) {
		if ((Boolean) XposedHelpers.callStaticMethod(mActivityManagerNativeClass, "isSystemReady")) {
			XposedHelpers.callMethod(
					XposedHelpers.callStaticMethod(mActivityManagerNativeClass, "getDefault"), 
					"closeSystemDialogs", 
					new Class<?>[]{String.class}, 
					reason
			);
		}
    }
	
	@SuppressLint("InlinedApi")
	private void triggerKeyEvent(final int keyCode) {
		KeyEvent downEvent = null;
		
		if (SDK_NUMBER > 10) {
			long now = SystemClock.uptimeMillis();
			
	        downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
	        		keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
	                	KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);

		} else {
			downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
		}
		
		KeyEvent upEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP);
		
		if (SDK_NUMBER > 10) {
			Object inputManager = XposedHelpers.callStaticMethod(mInputManagerClass, "getInstance");
			
			XposedHelpers.callMethod(inputManager, "injectInputEvent", new Class<?>[]{KeyEvent.class, Integer.TYPE}, downEvent, INJECT_INPUT_EVENT_MODE_ASYNC);
			XposedHelpers.callMethod(inputManager, "injectInputEvent", new Class<?>[]{KeyEvent.class, Integer.TYPE}, upEvent, INJECT_INPUT_EVENT_MODE_ASYNC);
			
		} else {
			XposedHelpers.callMethod(mWindowManager, "injectInputEventNoWait", new Class<?>[]{KeyEvent.class}, downEvent);
			XposedHelpers.callMethod(mWindowManager, "injectInputEventNoWait", new Class<?>[]{KeyEvent.class}, upEvent);
		}
	}
	
	private Boolean isRotationLocked() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) == 0;
	}
	
	private Integer getRotation() {
		return (Integer) XposedHelpers.callMethod(mWindowManager, "getRotation");
	}
	
	private void freezeRotation(Integer orientation) {
		if (SDK_NUMBER > 10) {
			XposedHelpers.callMethod(
					mWindowManager, 
					"freezeRotation",
					new Class<?>[]{Integer.TYPE},
					orientation
			);
			
		} else {
			/*
			 * TODO: Find a working way for locking Gingerbread in a specific orientation
			 */
			
			/* if (orientation < 0) {
				orientation = getRotation();
			} */
			
			Settings.System.putInt(mContext.getContentResolver(), 
					Settings.System.ACCELEROMETER_ROTATION, 0);
			
			/* XposedHelpers.callMethod(
					mWindowManager, 
					"setRotationUnchecked",
					new Class<?>[]{Integer.TYPE, Boolean.TYPE, Integer.TYPE},
					orientation, false, 0
			); */
		}
	}
	
	private void thawRotation() {
		if (SDK_NUMBER > 10) {
			XposedHelpers.callMethod(mWindowManager, "thawRotation");	
			
		} else {
			Settings.System.putInt(mContext.getContentResolver(), 
					Settings.System.ACCELEROMETER_ROTATION, 1);
		}
	}
	
	private Integer getNextRotation(Integer position) {
		Integer current=0, surface, next;
		Integer[] positions = new Integer[]{
			Surface.ROTATION_0,
			Surface.ROTATION_90,
			Surface.ROTATION_180,
			Surface.ROTATION_270
		};

		surface = getRotation();
		
		for (int i=0; i < positions.length; i++) {
			if ((int) positions[i] == (int) surface) {
				current = i + position; break;
			}
		}
		
		next = current >= positions.length ? 0 : 
				(current < 0 ? positions.length-1 : current);
		
		return positions[next];
	}
	
	private void rotateRight() {
		freezeRotation( getNextRotation(1) );
	}
	
	private void rotateLeft() {
		freezeRotation( getNextRotation(-1) );
	}
	
	private void toggleRotation() {
		if (isRotationLocked()) {
			thawRotation();
			Toast.makeText(mContext, "Rotation has been Enabled", Toast.LENGTH_SHORT).show();
			
		} else {
			freezeRotation(-1);
			Toast.makeText(mContext, "Rotation has been Disabled", Toast.LENGTH_SHORT).show();
		}
	}
}
