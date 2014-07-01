package com.spazedog.xposed.additionsgb.backend.pwm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.utils.ReflectException;
import com.spazedog.xposed.additionsgb.backend.pwm.KeyFlags.State;
import com.spazedog.xposed.additionsgb.backend.pwm.Mediator.ActionType;
import com.spazedog.xposed.additionsgb.backend.pwm.Mediator.SDK;
import com.spazedog.xposed.additionsgb.backend.pwm.Mediator.StackAction;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager.XServiceBroadcastListener;

import de.robv.android.xposed.XC_MethodHook;

public final class PhoneWindowManager {
	public static final String TAG = PhoneWindowManager.class.getName();
	
	private XServiceManager mXServiceManager;
	private Mediator mMediator;
	private KeySetup mKeySetup;
	private KeyFlags mKeyFlags;
	
	private Boolean mInterceptKeyCode = false;
	
	private Boolean mActiveQueueing = false;
	private Boolean mActiveDispatching = false;
	
	private Boolean wasScreenOn = true;

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
								mMediator = new Mediator(pwm, mXServiceManager);
								
								if (mMediator.isReady()) {
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
									 * Create key class instances for key control
									 */
									mKeySetup = new KeySetup(mXServiceManager);
									mKeyFlags = new KeyFlags(mXServiceManager);
									
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
			if (mKeyFlags.hasState(State.ONGOING, State.DEFAULT)) {
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
			synchronized(hook_interceptKeyBeforeQueueing) {
				mActiveQueueing = true;
				
				Integer methodVersion = Mediator.SDK.METHOD_INTERCEPT_VERSION;
				KeyEvent keyEvent = methodVersion == 1 ? null : (KeyEvent) param.args[0];
				Integer action = (Integer) (methodVersion == 1 ? param.args[1] : keyEvent.getAction());
				Integer policyFlags = (Integer) (methodVersion == 1 ? param.args[5] : param.args[1]);
				Integer policyFlagsPos = methodVersion == 1 ? 5 : 1;
				Integer eventFlags = (Integer) (methodVersion == 1 ? param.args[2] : keyEvent.getFlags());
				Integer keyCode = (Integer) (methodVersion == 1 ? param.args[3] : keyEvent.getKeyCode());
				Integer repeatCount = (Integer) (methodVersion == 1 ? 0 : keyEvent.getRepeatCount());
				Boolean isScreenOn = (Boolean) (methodVersion == 1 ? param.args[6] : param.args[2]);
				Boolean down = action == KeyEvent.ACTION_DOWN;
				
				/*
				 * Using KitKat work-around from the InputManager Hook
				 */
				Boolean isInjected = Mediator.SDK.MANAGER_HARDWAREINPUT_VERSION > 1 ? 
						(((KeyEvent) param.args[0]).getFlags() & Mediator.ORIGINAL.FLAG_INJECTED) != 0 : (policyFlags & Mediator.ORIGINAL.FLAG_INJECTED) != 0;
				
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
						param.setResult(Mediator.ORIGINAL.QUEUEING_ALLOW);
						
					} else if ((policyFlags & Mediator.ORIGINAL.FLAG_INJECTED) != 0) {
						/*
						 * Some ROM's disables features on injected keys. So let's remove the flag.
						 */
						param.args[policyFlagsPos] = policyFlags & ~Mediator.ORIGINAL.FLAG_INJECTED;
					}
					
				/*
				 * No need to do anything if the settings part of the module
				 * has asked for the keys. However, do make sure that the screen is on.
				 * The display could have been auto turned off while in the settings remap part.
				 * We don't want to create a situation where users can't turn the screen back on.
				 */
				} else if (mInterceptKeyCode && isScreenOn) {
					if (down) {
						mMediator.performHapticFeedback(keyEvent, HapticFeedbackConstants.VIRTUAL_KEY, policyFlags);
						
					} else if (mMediator.validateDeviceType(keyEvent == null ? keyCode : keyEvent)) {
						Bundle bundle = new Bundle();
						bundle.putInt("keyCode", keyCode);
						
						/*
						 * Send the key back to the settings part
						 */
						mXServiceManager.sendBroadcast("keyIntercepter:keyCode", bundle);
					}
					
					param.setResult(Mediator.ORIGINAL.QUEUEING_REJECT);
					
				} else {
					/*
					 * Check to see if this is a new event (Which means not a continued tap event or a general key up event).
					 */
					if (mKeyFlags.registerKey(keyCode, down, eventFlags)) {
						/*
						 * Make sure that we have a valid and supported device type
						 */
						if (mMediator.validateDeviceType(keyEvent == null ? keyCode : keyEvent)) {
							/*
							 * Prepare the event information for this key or key combo.
							 */
							mKeySetup.registerEvent(mKeyFlags.getPrimaryKey(), mKeyFlags.getSecondaryKey(), mKeyFlags.isComboAction(), mMediator.getPackageNameFromStack(0, StackAction.INCLUDE_HOME), mMediator.isKeyguardLocked(), isScreenOn);
							
							/*
							 * If the screen is off, it's a good idea to poke the device out of deep sleep. 
							 */
							if (!isScreenOn) {
								mMediator.pokeUserActivity(mKeyFlags.getDownTime(), false);
							}
							
							wasScreenOn = isScreenOn;
							
						} else {
							/*
							 * Don't handle this event
							 */
							mKeyFlags.refresh();
							
							return;
						}
					}
					
					if (down) {
						mMediator.performHapticFeedback(keyEvent, HapticFeedbackConstants.VIRTUAL_KEY, policyFlags);
					}
					
					param.setResult(Mediator.ORIGINAL.QUEUEING_ALLOW);
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
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			mActiveDispatching = true;
			
			Integer methodVersion = Mediator.SDK.METHOD_INTERCEPT_VERSION;
			KeyEvent keyEvent = methodVersion == 1 ? null : (KeyEvent) param.args[1];
			Integer keyCode = (Integer) (methodVersion == 1 ? param.args[3] : keyEvent.getKeyCode());
			Integer action = (Integer) (methodVersion == 1 ? param.args[1] : keyEvent.getAction());
			Integer policyFlags = (Integer) (methodVersion == 1 ? param.args[7] : param.args[2]);
			Integer policyFlagsPos = methodVersion == 1 ? 7 : 2;
			Integer eventFlags = (Integer) (methodVersion == 1 ? param.args[2] : keyEvent.getFlags());
			Integer repeatCount = (Integer) (methodVersion == 1 ? param.args[6] : keyEvent.getRepeatCount());
			Boolean down = action == KeyEvent.ACTION_DOWN;
			
			/*
			 * Using KitKat work-around from the InputManager Hook
			 */
			Boolean isInjected = Mediator.SDK.MANAGER_HARDWAREINPUT_VERSION > 1 ? 
					(((KeyEvent) param.args[1]).getFlags() & Mediator.ORIGINAL.FLAG_INJECTED) != 0 : (policyFlags & Mediator.ORIGINAL.FLAG_INJECTED) != 0;
			
			if (isInjected) {
				if (down && mKeyFlags.isKeyDown() && mKeyFlags.hasState(State.DEFAULT) && mKeyFlags.getCurrentKey() == keyCode) {
					mMediator.injectInputEvent(keyEvent == null ? keyCode : keyEvent, KeyEvent.ACTION_DOWN, mKeyFlags.getDownTime(), mKeyFlags.getEventTime(), repeatCount+1, eventFlags);
				}
				
				if ((policyFlags & Mediator.ORIGINAL.FLAG_INJECTED) != 0) {
					param.args[policyFlagsPos] = policyFlags & ~Mediator.ORIGINAL.FLAG_INJECTED;
				}
				
				return;
				
			} else if (!down && !mKeyFlags.isKeyDown() && mKeyFlags.hasState(State.DEFAULT) && mKeyFlags.getKeyLevel(keyCode) > 0) {
				mMediator.injectInputEvent(keyEvent == null ? keyCode : keyEvent, KeyEvent.ACTION_UP, mKeyFlags.getDownTime(), mKeyFlags.getEventTime(), repeatCount+1, eventFlags);
				
				if (mKeyFlags.isComboAction()) {
					Integer primary = mKeyFlags.getKeyLevel(keyCode) < 2 ? mKeyFlags.getSecondaryKey() : mKeyFlags.getPrimaryKey();
					Integer flags = mKeyFlags.getKeyLevel(keyCode) < 2 ? mKeyFlags.getSecondaryFlags() : mKeyFlags.getPrimaryFlags();
					
					mMediator.injectInputEvent(primary, KeyEvent.ACTION_UP, mKeyFlags.getDownTime(), mKeyFlags.getEventTime(), 0, flags);
				}
				
			} else if (mKeyFlags.hasState(State.ONGOING)) {
				if (down) {
					Integer pressTimeout = mKeySetup.getPressTimeout();
					Integer curTimeout = 0;
					
					do {
						try {
							Thread.sleep(10);
							
						} catch (Throwable e) {}
						
						curTimeout += 10;
						
					} while (mKeyFlags.isKeyDown() && keyCode == mKeyFlags.getCurrentKey() && curTimeout < pressTimeout);
					
					synchronized(hook_interceptKeyBeforeQueueing) {
						if (mKeyFlags.isKeyDown() && keyCode == mKeyFlags.getCurrentKey()) {
							if (mKeySetup.hasPress( mKeyFlags.getTapCount() )) {
								mKeyFlags.invoke();
								mMediator.handleKeyAction(mKeySetup.getPressAction( mKeyFlags.getTapCount() ), ActionType.PRESS, keyCode, mKeyFlags.getDownTime(), eventFlags, policyFlags, wasScreenOn);
								
							} else {
								mKeyFlags.invoke(true);
								
								if (mKeyFlags.isComboAction()) {
									Integer primary = mKeyFlags.getKeyLevel(keyCode) < 2 ? mKeyFlags.getSecondaryKey() : mKeyFlags.getPrimaryKey();
									Integer flags = mKeyFlags.getKeyLevel(keyCode) < 2 ? mKeyFlags.getSecondaryFlags() : mKeyFlags.getPrimaryFlags();
									
									mMediator.injectInputEvent(primary, KeyEvent.ACTION_DOWN, mKeyFlags.getDownTime(), mKeyFlags.getEventTime(), 0, flags);
								}
								
								/*
								 * This will start a chain reaction of injections to imitate long press
								 */
								mMediator.injectInputEvent(keyEvent == null ? keyCode : keyEvent, KeyEvent.ACTION_DOWN, mKeyFlags.getDownTime(), mKeyFlags.getEventTime(), 0, eventFlags);
								
								/*
								 * The first one MUST be dispatched throughout the system.
								 * Applications can ONLY start tracking from the original event object.
								 */
								param.setResult(Mediator.ORIGINAL.DISPATCHING_ALLOW); 
								
								return;
							}
						}
					}
					
				} else {
					if ((mKeySetup.hasTapClick() || mKeySetup.hasTapPress()) && mKeyFlags.getTapCount() < 3) {
						Integer tapTimeout = mKeySetup.getTapTimeout();
						Integer curTimeout = 0;
						
						do {
							try {
								Thread.sleep(10);
								
							} catch (Throwable e) {}
							
							curTimeout += 10;
							
						} while (!mKeyFlags.isKeyDown() && curTimeout < tapTimeout);
					}
					
					synchronized(hook_interceptKeyBeforeQueueing) {
						if (!mKeyFlags.isKeyDown() && mKeyFlags.getCurrentKey() == keyCode) {
							mKeyFlags.invoke();
							
							if (mKeyFlags.getTapCount() < 3 && mKeySetup.hasClick( mKeyFlags.getTapCount() )) {
								mMediator.handleKeyAction(mKeySetup.getClickAction( mKeyFlags.getTapCount() ), ActionType.TAP, keyCode, mKeyFlags.getDownTime(), eventFlags, policyFlags, wasScreenOn);
								
							} else {
								for (int i=0; i <= mKeyFlags.getTapCount(); i++) {
									if (i == 0 && mKeyFlags.isCallButton() && mMediator.invokeCallButton()) {
										continue;
									}
									
									mMediator.handleKeyAction(mKeySetup.getClickAction(0), ActionType.CLICK, keyCode, mKeyFlags.getDownTime(), eventFlags, policyFlags, wasScreenOn);
								}
							}
						}
					}
				}
				
			} else if (mKeyFlags.hasState(State.PENDING)) {
				/*
				 * The module is not handling this event 
				 */
				return;
			}
			
			param.setResult(Mediator.ORIGINAL.DISPATCHING_REJECT);
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
