package com.spazedog.xposed.additionsgb.hooks;

import java.lang.ref.WeakReference;
import java.lang.reflect.Member;
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
import com.spazedog.xposed.additionsgb.Common.HapticFeedbackLw;
import com.spazedog.xposed.additionsgb.tools.XposedTools;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class PhoneWindowManager extends XC_MethodHook {
	
	public static final String TAG = Common.PACKAGE_NAME + "$PhoneWindowManager";
	
	public static final Boolean DEBUG = Common.DEBUG;
	
	private PhoneWindowManager() {}
	
	public static void inject() {
		Class<?> phoneWindowManagerClass = XposedTools.findClass("com.android.internal.policy.impl.PhoneWindowManager");
			
		PhoneWindowManager instance = new PhoneWindowManager();
			
		XposedTools.hookConstructors(phoneWindowManagerClass, instance);
		XposedTools.hookMethods(phoneWindowManagerClass, "init", instance);
		XposedTools.hookMethods(phoneWindowManagerClass, "interceptKeyBeforeQueueing", instance);
		XposedTools.hookMethods(phoneWindowManagerClass, "interceptKeyBeforeDispatching", instance);
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
	private int ACTION_DISPATCH;
	private int ACTION_DISABLE = 0;
	private int INJECT_INPUT_EVENT_MODE_ASYNC;
	
	private static final int SDK_NUMBER = android.os.Build.VERSION.SDK_INT;
	
	private WeakReference<Object> mHookedReference;
	
	private Context mContext;
	private Object mWindowManager;
	private PowerManager mPowerManager;
	private WakeLock mWakeLock;
	private WakeLock mWakeLockPartial;
	private Handler mHandler;
	HapticFeedbackLw mHapticFeedbackLw;
	
	private Object mRecentAppsTrigger;
	
	private Class<?> mActivityManagerNativeClass;
	private Class<?> mWindowManagerPolicyClass; 
	private Class<?> mServiceManagerClass;
	private Class<?> mInputManagerClass;
	
	protected Boolean mInterceptKeycode = false;
	
	protected int mKeyPressDelay = 0;
	protected int mKeyTapDelay = 0;
	protected int mKeyPrimary = 0;
	protected int mKeySecondary = 0;
	protected int mKeyCode = 0;
	protected String mKeyAction;
	protected final Flags mKeyFlags = new Flags();
	
	protected Integer mInternalQueryResult;
	protected Object[] mInternalQueryArgs;
	protected Member mInternalQueryMethod;
	
	protected Boolean mWasScreenOn = true;
	
	protected Boolean mIsUnlocked = false;
	
	protected class Flags {
		public volatile Boolean DOWN = false;
		public volatile Boolean CANCEL = false;
		public volatile Boolean RESET = false;
		public volatile Boolean ONGOING = false;
		public volatile Boolean DEFAULT = false;
		public volatile Boolean REPEAT = false;
		public volatile Boolean MULTI = false;
		public volatile Boolean INJECTED = false;
		public volatile Boolean DISPATCHED = false;
		public volatile Boolean HAS_TAP = false;
		public volatile Boolean HAS_MULTI = false;
		
		public void reset() {
			DOWN = false;
			CANCEL = false;
			RESET = false;
			ONGOING = false;
			DEFAULT = false;
			REPEAT = false;
			MULTI = false;
			INJECTED = false;
			DISPATCHED = false;
			HAS_TAP = false;
			HAS_MULTI = false;
		}
	}
	
	protected final Runnable mMappingRunnable = new Runnable() {
        @SuppressLint("NewApi")
		@Override
        public void run() {
        	if (!mKeyFlags.MULTI) {
        		mKeyFlags.CANCEL = true;
        		mKeyFlags.RESET = true;
        		
        	} else {
        		mKeyFlags.CANCEL = true;
        	}
        	
        	if (mKeyFlags.REPEAT || mKeyFlags.DOWN) {
        		performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        	}
        	
        	if (mKeyAction.equals("default")) {
        		mKeyAction = "" + mKeyPrimary;
        	}
        	
        	if (mKeyAction.equals("disabled")) {
        		if(DEBUG)Common.log(TAG, "Handler: Action is disabled, skipping");
        		
        		// Disable the button
        		
        	} else if (mKeyAction.equals("poweron")) { 
        		if(DEBUG)Common.log(TAG, "Handler: Invoking forced power on");

        		if (SDK_NUMBER >= 17) {
        			mPowerManager.wakeUp(SystemClock.uptimeMillis());
        		
        		} else {
        			aquireWakelock();
        			mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
        		}
        		
        	} else if (mKeyAction.equals("poweroff")) { 
        		if(DEBUG)Common.log(TAG, "Handler: Invoking forced power off");
        		
        		/*
        		 * Parsing the power code does not always work on Gingerbread.
        		 * So like poweron, we also make a poweroff to force the device off.
        		 */
        		mPowerManager.goToSleep(SystemClock.uptimeMillis());
        		
        	} else if (mKeyAction.equals("recentapps")) {
        		if(DEBUG)Common.log(TAG, "Handler: Invoking Recent Apps dialog");
        		
        		openRecentAppsDialog();
        		
        	} else if (mKeyAction.equals("powermenu")) {
        		if(DEBUG)Common.log(TAG, "Handler: Invoking Power Menu dialog");
        		
        		openGlobalActionsDialog();
        		
        	} else if (mKeyAction.equals("flipleft")) {
        		if(DEBUG)Common.log(TAG, "Handler: Invoking left orientation");
        		
        		rotateLeft();
        		
        	} else if (mKeyAction.equals("flipright")) {
        		if(DEBUG)Common.log(TAG, "Handler: Invoking right orientation");
        		
        		rotateRight();
        		
        	} else if (mKeyAction.equals("fliptoggle")) {
        		if(DEBUG)Common.log(TAG, "Handler: Invoking orientation toggle");
        		
        		toggleRotation();
        		
        	} else {
        		if(DEBUG)Common.log(TAG, "Handler: Injecting key code " + mKeyAction);
        		
        		Integer keyCode = Integer.parseInt(mKeyAction);
        		
        		mKeyFlags.INJECTED = true;
				
				triggerKeyEvent(keyCode);
        	}
        }
	};
	
	protected final Runnable mReleasePartialWakelock = new Runnable() {
        @Override
        public void run() {
        	if (mWakeLockPartial.isHeld()) {
        		if(DEBUG)Common.log(TAG, "Releasing partial wakelock");
        		
        		mWakeLockPartial.release();
        	}
        }
	};
	
    protected final Runnable mReleaseWakelock = new Runnable() {
        @Override
        public void run() {
        	if (mWakeLock.isHeld()) {
        		if(DEBUG)Common.log(TAG, "Releasing wakelock");
        		
        		mWakeLock.release();
        	}
        }
    };
	
	private void hook_construct(final MethodHookParam param) {
		mHookedReference = new WeakReference<Object>(param.thisObject);
		
		mWindowManagerPolicyClass = XposedTools.findClass("android.view.WindowManagerPolicy");
		mActivityManagerNativeClass = XposedTools.findClass("android.app.ActivityManagerNative");
		mServiceManagerClass = XposedTools.findClass("android.os.ServiceManager");
		
		if (SDK_NUMBER >= 16) {
			mInputManagerClass = XposedTools.findClass("android.hardware.input.InputManager");
			INJECT_INPUT_EVENT_MODE_ASYNC = (Integer) XposedTools.getField(mInputManagerClass, "INJECT_INPUT_EVENT_MODE_ASYNC");
		}

		FLAG_INJECTED = (Integer) XposedTools.getField(mWindowManagerPolicyClass, "FLAG_INJECTED");
		FLAG_VIRTUAL = (Integer) XposedTools.getField(mWindowManagerPolicyClass, "FLAG_VIRTUAL");
		ACTION_DISPATCH = (Integer) XposedTools.getField(mWindowManagerPolicyClass, "ACTION_PASS_TO_USER");
	}
	
	/**
	 * Gingerbread uses arguments init(Context, IWindowManager, LocalPowerManager)
	 * ICS uses arguments init(Context, IWindowManager, WindowManagerFuncs, LocalPowerManager)
	 * JellyBean uses arguments init(Context, IWindowManager, WindowManagerFuncs)
	 */
	private void hook_init(final MethodHookParam param) {
    	mContext = (Context) param.args[0];
    	mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    	mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "PhoneWindowManagerHook");
    	mWakeLockPartial = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneWindowManagerHookPartial");
    	mHandler = new Handler();
    	mWindowManager = param.args[1];
    	mIsUnlocked = Common.isUnlocked(mContext);
    	mHapticFeedbackLw = new HapticFeedbackLw(mContext);
    	
    	mContext.registerReceiver(
    		new BroadcastReceiver() {
	    		@Override
	    		public void onReceive(Context context, Intent intent) {
	    			if (intent.getStringExtra("request").equals(Common.BroadcastOptions.REQUEST_ENABLE_KEYCODE_INTERCEPT)) {
	    				mInterceptKeycode = true;
	    				
	    			} else if (intent.getStringExtra("request").equals(Common.BroadcastOptions.REQUEST_DISABLE_KEYCODE_INTERCEPT)) {
	    				mInterceptKeycode = false;
	    				
	    			} else if (intent.getStringExtra("request").equals(Common.BroadcastOptions.REQUEST_RELOAD_CONFIGS)) {
	    				Common.loadSharedPreferences(null, true);
	    				
	    				mIsUnlocked = Common.isUnlocked(mContext);
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
		final int action = (Integer) (SDK_NUMBER <= 10 ? param.args[1] : ((KeyEvent) param.args[0]).getAction());
		final int policyFlags = (Integer) (SDK_NUMBER <= 10 ? param.args[5] : param.args[1]);
		final int keyCode = (Integer) (SDK_NUMBER <= 10 ? param.args[3] : ((KeyEvent) param.args[0]).getKeyCode());
		final boolean isScreenOn = (Boolean) (SDK_NUMBER <= 10 ? param.args[6] : param.args[2]);
		final boolean down = action == KeyEvent.ACTION_DOWN;
		
		boolean setup = false;
		
		/*
		 * Do not handle injected keys. 
		 * They might even be our own doing or could for other
		 * reasons create a loop or break something.
		 */
		if ((policyFlags & FLAG_INJECTED) != 0) {
			if(DEBUG && down)Common.log(TAG, "Queueing: Key code " + keyCode + " was injected, skipping");
			
			if (mKeyFlags.INJECTED) {
				param.args[ (SDK_NUMBER <= 10 ? 5 : 1) ] = policyFlags ^ FLAG_INJECTED;
				
				if (!down) {
					mKeyFlags.INJECTED = false;
				}
			}
			
			return;
		}
		
		/*
		 * During the first key down while the screen is off,
		 * we will reset everything to avoid issues if the screen was turned off by 
		 * a virtual key. Those keys does not execute key up when the screen is off. 
		 */
		if (down && ((!isScreenOn && isScreenOn != mWasScreenOn) || mKeyFlags.RESET)) {
			if(DEBUG)Common.log(TAG, "Queueing: Re-setting old flags");
			
			mKeyFlags.reset();
		}
		
		if (mInterceptKeycode && isScreenOn) {
			if (down) {
				if ((policyFlags & FLAG_VIRTUAL) != 0) {
					performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
				}
				
				sendBroadcastResponse(keyCode);
			}
			
			param.setResult(ACTION_DISABLE);
			
			return;
		}
		
		if (!down && !mKeyFlags.ONGOING) {
			if(DEBUG)Common.log(TAG, "Queueing: The key code " + keyCode + " is not ours. Returning it to the original handler");
			
			return;
			
		} else if (!down) {
			if (keyCode == mKeyPrimary || (mKeyFlags.MULTI && keyCode == mKeySecondary)) {
				if(DEBUG)Common.log(TAG, "Queueing: Releasing " + (keyCode == mKeyPrimary ? "primary" : "secondary") + " key");
				
				mKeyFlags.DOWN = false;
				
				if (keyCode == mKeyPrimary && (mKeyFlags.MULTI || mKeyFlags.REPEAT)) {
					mKeyFlags.RESET = true;
					
				} else if (keyCode == mKeySecondary && mKeyFlags.MULTI && mKeyFlags.REPEAT) {
					mKeyFlags.REPEAT = false;
				}

			} else {
				if(DEBUG)Common.log(TAG, "Queueing: The key code " + keyCode + " is not ours. Disabling it as we have an ongoing event");
				
				param.setResult(ACTION_DISABLE);
				
				return;
			}
		}
		
		if (down) {
			if (mKeyFlags.ONGOING && (keyCode == mKeyPrimary || keyCode == mKeySecondary) && mKeyFlags.HAS_TAP) {
				if(DEBUG)Common.log(TAG, "Queueing: Registering key repeat flag");
				
				mKeyFlags.REPEAT = true;
				mKeyFlags.DOWN = true;
				
			} else if (mKeyFlags.ONGOING && (mKeySecondary == 0 || !mKeyFlags.DOWN) && !mKeyFlags.REPEAT && mKeyFlags.HAS_MULTI) {
				if(DEBUG)Common.log(TAG, "Queueing: Adding secondary key on " + keyCode);
				
				mKeyFlags.MULTI = true;
				mKeyFlags.DOWN = true;
				mKeyFlags.CANCEL = false;
				mKeyFlags.DEFAULT = false;
				
				mKeySecondary = keyCode;
				
				setup = true;
				
			} else {
				if(DEBUG)Common.log(TAG, "Queueing: Starting new event on key code " + keyCode);
				
				mKeyFlags.ONGOING = true;
				mKeyFlags.DOWN = true;
				
				mKeyPrimary = keyCode;
				mKeySecondary = 0;
				mKeyPressDelay = Common.Remap.getPressDelay();
				mKeyTapDelay = Common.Remap.getTapDelay();
				
				setup = true;
			}
		}
		
		removeHandler();
		
		if (setup) {
			if(DEBUG)Common.log(TAG, "Queueing: Setting up " + (keyCode == mKeyPrimary ? "primary" : "secondary") + " key");
			
			mKeyCode = Common.generateKeyCode(mKeyPrimary, mKeySecondary);
			mWasScreenOn = isScreenOn;
			
			if (!Common.Remap.isKeyEnabled(mKeyCode, !isScreenOn)) {
				if (!mKeyFlags.MULTI) {
					if(DEBUG)Common.log(TAG, "Queueing: The " + (keyCode == mKeyPrimary ? "primary" : "secondary") + " key is not enabled, returning it to the original method");
					
					mKeyFlags.reset(); return;
					
				} else {
					if(DEBUG)Common.log(TAG, "Queueing: The secondary key is not enabled. Converting it into a primary key and starting a new event");
					
					mKeyPrimary = mKeyCode = mKeySecondary;
					mKeySecondary = 0;
					mKeyFlags.MULTI = false;
					
					if (!Common.Remap.isKeyEnabled(mKeyCode, !isScreenOn)) {
						if(DEBUG)Common.log(TAG, "Queueing: The " + (keyCode == mKeyPrimary ? "primary" : "secondary") + " key is not enabled, returning it to the original method");
						
						mKeyFlags.reset(); return;
					}
				}
			}
			
			if (mIsUnlocked) {
				if(DEBUG)Common.log(TAG, "Queueing: Enabling Multi and Tap options");
				
				String tabAction = Common.Remap.getKeyTap(mKeyCode, !isScreenOn);
				
				mKeyFlags.HAS_MULTI = true;
				mKeyFlags.HAS_TAP = (tabAction != null && !tabAction.equals("disabled"));
				
			} else {
				if(DEBUG)Common.log(TAG, "Queueing: Disabling Multi and Tap options");
				
				mKeyFlags.HAS_MULTI = mKeyFlags.HAS_TAP = false;
			}
			
			if (!isScreenOn) {
				aquirePartialWakelock();
				
			} else {
				releasePartialWakelock();
			}
			
			releaseWakelock();
			
			if ((policyFlags & FLAG_VIRTUAL) != 0) {
				performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
			}
		}
		
		if (!down) {
			if (!mKeyFlags.REPEAT && !mKeyFlags.CANCEL) {
				if(DEBUG)Common.log(TAG, "Queueing: Invoking click handler on the " + (keyCode == mKeyPrimary ? "primary" : "secondary") + " key");
				
				mKeyFlags.DEFAULT = false;

				mKeyAction = Common.Remap.getKeyClick(mKeyCode, !isScreenOn);
				
				if (mKeyFlags.HAS_TAP) {
					invokeHandler(mKeyTapDelay);
					
				} else {
					invokeHandler(0);
				}
				
				param.setResult(ACTION_DISABLE);
				
			} else {
				if (mKeyFlags.DEFAULT) {
					if(DEBUG)Common.log(TAG, "Queueing: Parsing long press release on the " + (keyCode == mKeyPrimary ? "primary" : "secondary") + " key to the dispatcher");
					
					mInternalQueryArgs = param.args;
					mInternalQueryMethod = param.method;
					
					param.setResult(ACTION_DISPATCH);
					
				} else {
					param.setResult(ACTION_DISABLE);
				}
			}
			
		} else {
			if (!mKeyFlags.REPEAT) {
				mKeyAction = Common.Remap.getKeyPress(mKeyCode, !isScreenOn);
				
				if (mKeyAction.equals("default")) {
					if(DEBUG)Common.log(TAG, "Queueing: Parsing long press event on the " + (keyCode == mKeyPrimary ? "primary" : "secondary") + " key to the dispatcher");
					
					mKeyFlags.DEFAULT = true;
					mKeyFlags.DISPATCHED = false;

					mInternalQueryArgs = param.args;
					mInternalQueryMethod = param.method;
					
					param.setResult(ACTION_DISPATCH);
					
				} else {
					if(DEBUG)Common.log(TAG, "Queueing: Invoking long press handler on the " + (keyCode == mKeyPrimary ? "primary" : "secondary") + " key");
					
					invokeHandler(mKeyPressDelay);
					
					param.setResult(ACTION_DISABLE);
				}
				
			} else {
				if(DEBUG)Common.log(TAG, "Queueing: Invoking tap handler on the " + (keyCode == mKeyPrimary ? "primary" : "secondary") + " key");
				
				mKeyAction = Common.Remap.getKeyTap(mKeyCode, !isScreenOn);
				
				invokeHandler(0);
				
				param.setResult(ACTION_DISABLE);
			}
		}
	}

	/**
	 * Gingerbread uses arguments interceptKeyBeforeDispatching(WindowState win, Integer action, Integer flags, Integer keyCode, Integer scanCode, Integer metaState, Integer repeatCount, Integer policyFlags)
	 * ICS/JellyBean uses arguments interceptKeyBeforeDispatching(WindowState win, KeyEvent event, Integer policyFlags)
	 */
	private void hook_interceptKeyBeforeDispatching(final MethodHookParam param) {
		Object dispatchDisabled = SDK_NUMBER <= 10 ? true : -1;
		
		final int keyCode = (Integer) (SDK_NUMBER <= 10 ? param.args[3] : ((KeyEvent) param.args[1]).getKeyCode());
		final int action = (Integer) (SDK_NUMBER <= 10 ? param.args[1] : ((KeyEvent) param.args[1]).getAction());
		final int repeatCount = (Integer) (SDK_NUMBER <= 10 ? param.args[6] : ((KeyEvent) param.args[1]).getRepeatCount());
		final int policyFlags = (Integer) (SDK_NUMBER <= 10 ? param.args[7] : param.args[2]);
		final boolean down = action == KeyEvent.ACTION_DOWN;
		
		if ((policyFlags & FLAG_INJECTED) != 0) {
			if(DEBUG && down)Common.log(TAG, "Dispatching: Key code " + keyCode + " was injected, skipping");
			
			if (mKeyFlags.INJECTED) {
				param.args[ (SDK_NUMBER <= 10 ? 7 : 2) ] = policyFlags ^ FLAG_INJECTED;
			}
			
			return;
		}
		
		if (mKeyFlags.ONGOING) {
			if (!mKeyFlags.DEFAULT) {
				if(DEBUG && down)Common.log(TAG, "Dispatching: Disabling dispatching on key code " + keyCode);
				
				param.setResult(dispatchDisabled);
				
			} else {
				if (!mKeyFlags.DISPATCHED && repeatCount == 0 && down) {
					if(DEBUG && down)Common.log(TAG, "Dispatching: Waiting for long press default action to be ready on key code " + mKeyPrimary);
					
					mKeyFlags.DISPATCHED = true;
					
					if (SDK_NUMBER <= 10) {
						try {
							Thread.sleep(mKeyTapDelay + ((int) mKeyTapDelay / 2));
							
						} catch (Throwable e) {}
						
						if (!mKeyFlags.DOWN || !mKeyFlags.DEFAULT) {
							param.setResult(dispatchDisabled); return;
						}
						
					} else {
						param.setResult(mKeyTapDelay + ((int) mKeyTapDelay / 2)); return;
					}
				}
				
				if (mKeySecondary > 0 && mKeySecondary != keyCode) {
					param.setResult(dispatchDisabled); return;
				}
				
				if ((repeatCount == 0 && down) || !down) {
					if(DEBUG && down)Common.log(TAG, "Dispatching: Invoking long press default action on key code " + keyCode);

					if (down) {
						mKeyFlags.REPEAT = true;
						mKeyFlags.CANCEL = true;
					}
					
					try {
						mInternalQueryResult = (Integer) XposedBridge.invokeOriginalMethod(mInternalQueryMethod, mHookedReference.get(), mInternalQueryArgs);
						
					} catch (Throwable e) { e.printStackTrace(); }
				}
				
				if (mInternalQueryResult != ACTION_DISPATCH) {
					if(DEBUG && down)Common.log(TAG, "Dispatching: Disabling dispatching on the key code " + keyCode);
					
					param.setResult(dispatchDisabled);
					
				} else {
					if(DEBUG && down)Common.log(TAG, "Dispatching: Parsing the key code " + keyCode + " to the original dispatcher");
					
					try {
						param.setResult(
								XposedBridge.invokeOriginalMethod(param.method, mHookedReference.get(), param.args)
						);
						
					} catch (Throwable e) { e.printStackTrace(); }
				}
			}
			
			return;
			
		} else {
			if(DEBUG && down)Common.log(TAG, "Dispatching: The key code " + keyCode + " is not on going, skipping");
		}
	}

	protected void sendBroadcastResponse(Integer value) {
		Intent intent = new Intent(Common.BroadcastOptions.INTENT_ACTION_RESPONSE);
		intent.putExtra("response", value);
		
		mContext.sendBroadcast(intent);
	}
	
	protected void removeHandler() {
		if (mKeyFlags.ONGOING) {
			if(DEBUG)Common.log(TAG, "Removing any existing pending handlers");
			
			mHandler.removeCallbacks(mMappingRunnable);
		}
	}
	
	protected void invokeHandler(Integer timeout) {
		if (timeout > 0) {
			mHandler.postDelayed(mMappingRunnable, timeout);
			
		} else {
			mHandler.post(mMappingRunnable);
		}
	}
	
	protected void releaseWakelock() {
		if (mWakeLock.isHeld()) {
			if(DEBUG)Common.log(TAG, "Releasing wakelock");
			
			mHandler.removeCallbacks(mReleaseWakelock);
			mWakeLock.release();
		}
	}
	
	protected void releasePartialWakelock() {
		if (mWakeLockPartial.isHeld()) {
			if(DEBUG)Common.log(TAG, "Releasing partial wakelock");
			
			mHandler.removeCallbacks(mReleasePartialWakelock);
			mWakeLockPartial.release();
		}
	}
	
	public void aquireWakelock() {
		if (mWakeLock.isHeld()) {
			mHandler.removeCallbacks(mReleaseWakelock);
			
		} else {
			mWakeLock.acquire();
		}
		
		if(DEBUG)Common.log(TAG, "Aquiring new wakelock");
		
		mHandler.postDelayed(mReleaseWakelock, 10000);
	}
	
	public void aquirePartialWakelock() {
		if (mWakeLockPartial.isHeld()) {
			mHandler.removeCallbacks(mReleasePartialWakelock);
			
		} else {
			mWakeLockPartial.acquire();
		}
		
		if(DEBUG)Common.log(TAG, "Aquiring new partial wakelock");
		
		mHandler.postDelayed(mReleasePartialWakelock, 3000);
	}

	private void openRecentAppsDialog() {
		sendCloseSystemWindows("recentapps");
		
		if (SDK_NUMBER > 10) {
			if (mRecentAppsTrigger == null) {
				mRecentAppsTrigger = IStatusBarService.Stub.asInterface(
					(IBinder) XposedTools.callMethod(
						mServiceManagerClass,
						"getService",
						new Class<?>[]{String.class},
						"statusbar"
					)
				);
			}
			
			XposedTools.callMethod(mRecentAppsTrigger, "toggleRecentApps");
			
		} else {
			if (mRecentAppsTrigger == null) {
				mRecentAppsTrigger = XposedTools.callConstructor(
						XposedTools.findClass("com.android.internal.policy.impl.RecentApplicationsDialog"),
						new Class<?>[]{Context.class},
						mContext
				);
			}
			
			try {
				XposedTools.callMethod(mRecentAppsTrigger, "show");
				
			} catch (Throwable e) { e.printStackTrace(); }
		}
	}

	private void openGlobalActionsDialog() {
		XposedTools.callMethod(mHookedReference.get(), "showGlobalActionsDialog");
	}
	
	private void performHapticFeedback(Integer effectId) {
		mHapticFeedbackLw.vibrate(effectId, false);
	}
	
	private void sendCloseSystemWindows(String reason) {
		if ((Boolean) XposedTools.callMethod(mActivityManagerNativeClass, "isSystemReady")) {
			XposedTools.callMethod(
					XposedTools.callMethod(mActivityManagerNativeClass, "getDefault"), 
					"closeSystemDialogs", 
					new Class<?>[]{String.class}, 
					reason
			);
		}
    }
	
	Method xInputEvent;
	Method xInputManager;
	@SuppressLint("InlinedApi")
	private void triggerKeyEvent(final int keyCode) {
		try {
			if (xInputEvent == null) {
				if (SDK_NUMBER >= 16) {
					xInputManager = XposedTools.findMethod(mInputManagerClass, "getInstance");
					xInputEvent = XposedTools.findMethod(mInputManagerClass, "injectInputEvent", KeyEvent.class, Integer.TYPE);
					
				} else {
					xInputEvent = XposedTools.findMethod(mWindowManager.getClass(), "injectInputEventNoWait", KeyEvent.class);
				}
			}
			
			KeyEvent downEvent = null;
			
			if (SDK_NUMBER >= 10) {
				long now = SystemClock.uptimeMillis();
				
		        downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
		        		keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
		                	KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
	
			} else {
				downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
			}
			
			KeyEvent upEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP);
			
			if (SDK_NUMBER >= 16) {
				Object inputManager = xInputManager.invoke(null);
				
				xInputEvent.invoke(inputManager, downEvent, INJECT_INPUT_EVENT_MODE_ASYNC);
				xInputEvent.invoke(inputManager, upEvent, INJECT_INPUT_EVENT_MODE_ASYNC);
				
			} else {
				xInputEvent.invoke(mWindowManager, downEvent);
				xInputEvent.invoke(mWindowManager, upEvent);
			}
			
		} catch (Throwable e) { e.printStackTrace(); }
	}
	
	private Boolean isRotationLocked() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) == 0;
	}
	
	Method xGetRotation;
	private Integer getRotation() {
		try {
			if (xGetRotation == null) {
				xGetRotation = XposedTools.findMethod(mWindowManager.getClass(), "getRotation");
			}
			
			return (Integer) xGetRotation.invoke(mWindowManager);
			
		} catch (Throwable e) { e.printStackTrace(); }
		
		return 0;
	}
	
	Method xRreezeRotation;
	private void freezeRotation(Integer orientation) {
		if (SDK_NUMBER > 10) {
			try {
				if (xRreezeRotation == null) {
					xRreezeRotation = XposedTools.findMethod(mWindowManager.getClass(), "freezeRotation", Integer.TYPE);
				}
				
				xRreezeRotation.invoke(mWindowManager, orientation);
				
			} catch (Throwable e) { e.printStackTrace(); }
			
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
	
	Method xThawRotation;
	private void thawRotation() {
		if (SDK_NUMBER > 10) {
			try {
				if (xThawRotation == null) {
					xThawRotation = XposedTools.findMethod(mWindowManager.getClass(), "thawRotation");
				}
				
				xThawRotation.invoke(mWindowManager);
				
			} catch (Throwable e) { e.printStackTrace(); }
			
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
