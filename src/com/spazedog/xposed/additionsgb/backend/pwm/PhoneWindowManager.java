package com.spazedog.xposed.additionsgb.backend.pwm;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.utils.ReflectException;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.backend.pwm.EventManager.State;
import com.spazedog.xposed.additionsgb.backend.pwm.iface.IEventMediator.ActionType;
import com.spazedog.xposed.additionsgb.backend.pwm.iface.IMediatorSetup.ORIGINAL;
import com.spazedog.xposed.additionsgb.backend.pwm.iface.IMediatorSetup.SDK;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager.XServiceBroadcastListener;

import de.robv.android.xposed.XC_MethodHook;

public final class PhoneWindowManager {
	public static final String TAG = PhoneWindowManager.class.getName();
	
	private XServiceManager mXServiceManager;
	private EventManager mEventManager;
	
	private Boolean mInterceptKeyCode = false;
	
	private Boolean mActiveQueueing = false;
	private Boolean mActiveDispatching = false;
	
	private final Object mQueueLock = new Object();

	/**
	 * This is a static initialization method.
	 */
	public static void init() {
		ReflectClass pwm = null;
		
		try {
			/*
			 * Start by getting the PhoneWindowManager class and
			 * create an instance of our own. 
			 */
			pwm = ReflectClass.forName("com.android.internal.policy.impl.PhoneWindowManager");
			PhoneWindowManager instance = new PhoneWindowManager();
			
			/*
			 * Hook the init method of the PhoneWindowManager class.
			 * This is our entry to it's process. It will be 
			 * the first thing to be invoked once the system is ready
			 * to use it. 
			 */
			pwm.inject("init", instance.hook_init);
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
			
			if (pwm != null) {
				/*
				 * Do not keep hooks on any kind of errors.
				 * Broken methods and such can result in system crash
				 * and boot loops. 
				 */
				pwm.removeInjections();
			}
		}
	}
	
	/**
	 * This method is used to hook the original PhoneWindowManager.init() method. 
	 * 
	 * Original Arguments:
	 * 		- Gingerbread: PhoneWindowManager.init(Context, IWindowManager, LocalPowerManager)
	 * 		- ICS: PhoneWindowManager.init(Context, IWindowManager, WindowManagerFuncs, LocalPowerManager)
	 * 		- JellyBean: PhoneWindowManager.init(Context, IWindowManager, WindowManagerFuncs)
	 */
	private final XC_MethodHook hook_init = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			/*
			 * Some Android services and such will not be ready yet.
			 * Let's wait until everything is up and running. 
			 */
			((Context) param.args[0]).registerReceiver(
				new BroadcastReceiver() {
					@Override
					public void onReceive(Context context, Intent intent) {
						/*
						 * Let's get an instance of our own Service Manager and
						 * make sure that the related service is running, before continuing.
						 */
						mXServiceManager = XServiceManager.getInstance();
						
						if (mXServiceManager != null) {
							ReflectClass pwm = null;
							
							try {
								/*
								 * Now we need to initialize our own Mediator. 
								 * And once again, do not continue without it as
								 * it contains all of our tools. 
								 */
								pwm = ReflectClass.forReceiver(param.thisObject);
								mEventManager = new EventManager(pwm, mXServiceManager);
								
								if (mEventManager.isReady()) {
									/*
									 * Add the remaining PhoneWindowManager hooks
									 */
									pwm.inject("interceptKeyBeforeQueueing", hook_interceptKeyBeforeQueueing);
									pwm.inject("interceptKeyBeforeDispatching", hook_interceptKeyBeforeDispatching);
									pwm.inject("performHapticFeedbackLw", hook_performHapticFeedbackLw);
									
									if (SDK.SAMSUNG_FEEDBACK_VERSION > 0) {
										ReflectClass spwm = ReflectClass.forName("com.android.internal.policy.impl.sec.SamsungPhoneWindowManager");
										spwm.inject("performSystemKeyFeedback", hook_performHapticFeedbackLw);
									}
									
									/*
									 * Add hooks to the ViewConfiguration class for this process,
									 * allowing us to control key timeout values that will affect the original class.
									 */
									ReflectClass wc = ReflectClass.forName("android.view.ViewConfiguration");
		
									wc.inject("getLongPressTimeout", hook_viewConfigTimeouts);
									wc.inject("getGlobalActionKeyTimeout", hook_viewConfigTimeouts);
									
									/*
									 * Add listener to receive broadcasts from the XService
									 */
									mXServiceManager.addBroadcastListener(listener_XServiceBroadcast);
								}
								
							} catch (Throwable e) {
								Log.e(TAG, e.getMessage(), e);
								
								if (pwm != null) {
									/*
									 * On error, disable this part of the module.
									 */
									pwm.removeInjections();
								}
							}
							
						} else {
							Log.e(TAG, "XService has not been started", null);
						}
					}
					
				}, new IntentFilter("android.intent.action.BOOT_COMPLETED")
			);
		}
	};
	
	/**
	 * A listener that is used used to receive key intercept requests from the settings part of the module.
	 */
	private final XServiceBroadcastListener listener_XServiceBroadcast = new XServiceBroadcastListener() {
		@Override
		public void onBroadcastReceive(String action, Bundle data) {
			if (action.equals("keyIntercepter:enable")) {
				mInterceptKeyCode = true;
				
			} else if (action.equals("keyIntercepter:disable")) {
				mInterceptKeyCode = false;
			}
		}
	};
	
	/**
	 * This does not really belong to the PhoneWindowManager class. 
	 * It is used as a small hack to change some internal method behavior, without
	 * affecting more than the original PhoneWindowManager process. 
	 * 
	 * Some ROM's uses the original implementations to set timeout on some handlers. 
	 * Like long press timeout for the power key when displaying the power menu. 
	 * But since we cannot change code inside original methods in order to change this timeout, 
	 * we instead change the output of the methods delivering that timeout value.
	 * 
	 * Original Implementations:
	 * 		- android.view.ViewConfiguration.getLongPressTimeout
	 * 		- android.view.ViewConfiguration.getGlobalActionKeyTimeout
	 */
	private final XC_MethodHook hook_viewConfigTimeouts = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			if (mEventManager.hasState(State.REPEATING) && mEventManager.isDownEvent()) {
				param.setResult(10);
			}
		}
	};
	
	/**
	 * This hook is used to make all of the preparations of key handling
	 * 
	 * Original Arguments
	 * 		- Gingerbread: PhoneWindowManager.interceptKeyBeforeQueueing(Long whenNanos, Integer action, Integer flags, Integer keyCode, Integer scanCode, Integer policyFlags, Boolean isScreenOn)
	 * 		- ICS & Above: PhoneWindowManager.interceptKeyBeforeQueueing(KeyEvent event, Integer policyFlags, Boolean isScreenOn)
	 */
	protected final XC_MethodHook hook_interceptKeyBeforeQueueing = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			synchronized(mQueueLock) {
				mActiveQueueing = true;

				Integer methodVersion = SDK.METHOD_INTERCEPT_VERSION;
				KeyEvent keyEvent = methodVersion == 1 ? null : (KeyEvent) param.args[0];
				Integer keyCode = (Integer) (methodVersion == 1 ? param.args[3] : keyEvent.getKeyCode());
				Object keyObject = keyEvent == null ? keyCode : keyEvent;
				Integer action = (Integer) (methodVersion == 1 ? param.args[1] : keyEvent.getAction());
				Integer policyFlags = (Integer) (methodVersion == 1 ? param.args[5] : param.args[1]);
				Integer policyFlagsPos = methodVersion == 1 ? 5 : 1;
				Integer repeatCount = (Integer) (methodVersion == 1 ? 0 : keyEvent.getRepeatCount());
				Boolean isScreenOn = (Boolean) (methodVersion == 1 ? param.args[6] : param.args[2]);
				Boolean down = action == KeyEvent.ACTION_DOWN;
				String tag = TAG + "#Queueing/" + (down ? "Down " : "Up ") + keyCode + "(" + mEventManager.getTapCount() + "," + repeatCount+ "):";
				
				Long downTime = methodVersion == 1 ? (((Long) param.args[0]) / 1000) / 1000 : keyEvent.getDownTime();
				Long eventTime = android.os.SystemClock.uptimeMillis();
				
				// android.os.SystemClock.uptimeMillis
				
				/*
				 * Using KitKat work-around from the InputManager Hook
				 */
				Boolean isInjected = SDK.MANAGER_HARDWAREINPUT_VERSION > 1 ? 
						(((KeyEvent) param.args[0]).getFlags() & ORIGINAL.FLAG_INJECTED) != 0 : (policyFlags & ORIGINAL.FLAG_INJECTED) != 0;
				
				/*
				 * The module should not handle injected keys. 
				 * First of all, we inject keys our self and would create a loop. 
				 * Second, some software buttons use injection, and we don't remap software keys.
				 */
				if (isInjected) {
					if (down && repeatCount > 0) {
						/*
						 * Normally repeated events will not continue to invoke this method. 
						 * But it seams that repeating an event using injection will. On most devices
						 * the original methods themselves seams to be handling this just fine, but a few 
						 * stock ROM's are treating these as both new and repeated events. 
						 */
						param.setResult(ORIGINAL.QUEUEING_ALLOW);
						
					} else if ((policyFlags & ORIGINAL.FLAG_INJECTED) != 0) {
						/*
						 * Some ROM's disables features on injected keys. So let's remove the flag.
						 */
						param.args[policyFlagsPos] = policyFlags & ~ORIGINAL.FLAG_INJECTED;
					}
					
				/*
				 * No need to do anything if the settings part of the module
				 * has asked for the keys. However, do make sure that the screen is on.
				 * The display could have been auto turned off while in the settings remap part.
				 * We don't want to create a situation where users can't turn the screen back on.
				 */
				} else if (mInterceptKeyCode && isScreenOn) {
					if (down) {
						mEventManager.performHapticFeedback(keyObject, HapticFeedbackConstants.VIRTUAL_KEY, policyFlags);
						
					} else if (mEventManager.validateDeviceType(keyObject)) {
						Bundle bundle = new Bundle();
						bundle.putInt("keyCode", keyCode);
						
						/*
						 * Send the key back to the settings part
						 */
						mXServiceManager.sendBroadcast("keyIntercepter:keyCode", bundle);
					}
					
					param.setResult(ORIGINAL.QUEUEING_REJECT);
					
				} else if (mEventManager.validateDeviceType(keyObject)) {
					/*
					 * Most ROM reboots after holding Power for 8-12s.
					 * For those missing (like Omate TrueSmart) this is kind of a replacement.
					 */
					mEventManager.powerHardResetTimer(keyCode, down);
					
					if (mEventManager.registerKey(keyCode, down, isScreenOn, policyFlags, downTime, eventTime)) {
						if(Common.debug()) Log.d(tag, "Starting a new event");
						
						/*
						 * Check to see if this is a new event (Which means not a continued tap event or a general key up event).
						 */
						mEventManager.setState(State.ONGOING);
						
						/*
						 * If the screen is off, it's a good idea to poke the device out of deep sleep. 
						 */
						if (!isScreenOn) {								
							mEventManager.pokeUserActivity(mEventManager.getEventTime(), false);
						}
						
					} else {
						if(Common.debug()) Log.d(tag, "Continuing ongoing event");
						
						if (down && !mEventManager.hasState(State.REPEATING)) {
							mEventManager.setState(State.ONGOING);
						}
					}
					
					if (down) {
						mEventManager.performHapticFeedback(keyObject, HapticFeedbackConstants.VIRTUAL_KEY, policyFlags);
					}
					
					if(Common.debug()) Log.d(tag, "Parsing the event to the queue (" + mEventManager.mState.name() + ")");
					
					param.setResult(ORIGINAL.QUEUEING_ALLOW);
				}
			}
		}
		
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			mActiveQueueing = false;
		}
	};
	
	/**
	 * This hook is used to do the actual handling of the keys
	 * 
	 * Original Arguments
	 * 		- Gingerbread: PhoneWindowManager.interceptKeyBeforeDispatching(WindowState win, Integer action, Integer flags, Integer keyCode, Integer scanCode, Integer metaState, Integer repeatCount, Integer policyFlags)
	 * 		- ICS & Above: PhoneWindowManager.interceptKeyBeforeDispatching(WindowState win, KeyEvent event, Integer policyFlags)
	 */	
	protected XC_MethodHook hook_interceptKeyBeforeDispatching = new XC_MethodHook() {
		@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			mActiveDispatching = true;
			
			Integer methodVersion = SDK.METHOD_INTERCEPT_VERSION;
			KeyEvent keyEvent = methodVersion == 1 ? null : (KeyEvent) param.args[1];
			Integer keyCode = (Integer) (methodVersion == 1 ? param.args[3] : keyEvent.getKeyCode());
			Integer action = (Integer) (methodVersion == 1 ? param.args[1] : keyEvent.getAction());
			Integer policyFlags = (Integer) (methodVersion == 1 ? param.args[7] : param.args[2]);
			Integer policyFlagsPos = methodVersion == 1 ? 7 : 2;
			Integer repeatCount = (Integer) (methodVersion == 1 ? param.args[6] : keyEvent.getRepeatCount());
			Boolean down = action == KeyEvent.ACTION_DOWN;
			EventKey key = mEventManager.getKey(keyCode);
			String tag = TAG + "#Dispatching/" + (down ? "Down " : "Up ") + keyCode + "(" + mEventManager.getTapCount() + "," + repeatCount+ "):";
			
			/*
			 * Using KitKat work-around from the InputManager Hook
			 */
			Boolean isInjected = SDK.MANAGER_HARDWAREINPUT_VERSION > 1 ? 
					(((KeyEvent) param.args[1]).getFlags() & ORIGINAL.FLAG_INJECTED) != 0 : (policyFlags & ORIGINAL.FLAG_INJECTED) != 0;
			
			if (isInjected) {
				/*
				 * When we disallow applications from getting the event, we also disable repeats. 
				 * This is a hack where we create a controlled injection loop to simulate repeats. 
				 * 
				 * If we did not have to support GB, then we could have just returned the timeout to force repeat without global dispatching. 
				 * But since we have GB to think about, this is the best solution. 
				 */
				if (down && key != null && key.getRepeatCount() > 0 && mEventManager.hasState(State.REPEATING) && mEventManager.isDownEvent()) {
					if(Common.debug()) Log.d(tag, "Injecting a new repeat " + key.getRepeatCount());
					
					Integer curTimeout = SDK.VIEW_CONFIGURATION_VERSION > 1 ? ViewConfiguration.getKeyRepeatDelay() : 50;
					Boolean continueEvent = mEventManager.waitForChange(curTimeout);
					
					synchronized(mQueueLock) {
						if (continueEvent && key.isPressed()) {
							key.invoke();
						}
					}
				}
				
				if ((policyFlags & ORIGINAL.FLAG_INJECTED) != 0) {
					param.args[policyFlagsPos] = policyFlags & ~ORIGINAL.FLAG_INJECTED;
				}
				
			} else if (key != null) {
				if (mEventManager.hasState(State.ONGOING)) {
					if (down) {
						if(Common.debug()) Log.d(tag, "Waiting on long press timeout");
						
						/*
						 * Long Press timeout
						 */
						Boolean continueEvent = mEventManager.waitForChange(mEventManager.getPressTimeout());
						
						synchronized(mQueueLock) {
							if (continueEvent && key.isLastQueued() && key.isPressed()) {
								String eventAction = mEventManager.getAction(ActionType.PRESS);
								mEventManager.setState(State.INVOKED);
								
								if (eventAction == null || !mEventManager.handleKeyAction(eventAction, ActionType.PRESS, mEventManager.getTapCount(), mEventManager.isScreenOn(), mEventManager.isCallButton(), mEventManager.getEventTime(), 0)) {
									if(Common.debug()) Log.d(tag, "Invoking default long press action");
									
									mEventManager.setState(State.REPEATING);
									key.invoke();
									
									/*
									 * The first one MUST be dispatched throughout the system.
									 * Applications can ONLY start tracking from the original event object.
									 */
									if(Common.debug()) Log.d(tag, "Parsing event to the dispatcher");
									
									param.setResult(ORIGINAL.DISPATCHING_ALLOW); 
									
									return;
								}
								
								if(Common.debug()) Log.d(tag, "Invoking custom long press action");
							}
						}
						
					} else {
						Boolean continueEvent = true;
						
						if (mEventManager.hasMoreActions()) {
							if(Common.debug()) Log.d(tag, "Waiting on tap timeout");
							
							/*
							 * Tap timeout
							 */
							continueEvent = mEventManager.waitForChange(mEventManager.getTapTimeout());
						}

						synchronized(mQueueLock) {
							if (continueEvent && key.isLastQueued() && !key.isPressed()) {
								if(Common.debug()) Log.d(tag, "Invoking Click Event");
								
								mEventManager.setState(State.INVOKED);

								String eventAction = mEventManager.getAction(ActionType.CLICK);
								
								if(Common.debug()) Log.d(tag, "Using action '" + (eventAction != null ? eventAction : "") + "'");
								
								if (!mEventManager.handleKeyAction(eventAction, ActionType.CLICK, mEventManager.getTapCount(), mEventManager.isScreenOn(), mEventManager.isCallButton(), mEventManager.getEventTime(), mEventManager.getTapCount() == 0 ? key.getFlags() : 0)) {
									key.invokeAndRelease();
								}
							}
						}
					}
					
				} else if (!down) {
					key.release();
				}
				
				if(Common.debug()) Log.d(tag, "Disabling default dispatching (" + mEventManager.mState.name() + ")");
				
				param.setResult(ORIGINAL.DISPATCHING_REJECT);
				
			} else if (Common.debug()) {
				Log.d(tag, "This key is not handled by the module");
			}
		}
		
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			mActiveDispatching = false;
		}
	};
	
	protected XC_MethodHook hook_performHapticFeedbackLw = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			if (mActiveQueueing || mActiveDispatching) {
				if (param.method.getName().equals("performSystemKeyFeedback") || (Integer) param.args[1] == HapticFeedbackConstants.VIRTUAL_KEY) {
					param.setResult(true);
				}
			}
		}
	};
}
