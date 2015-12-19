package com.spazedog.xposed.additionsgb.backend.pwm;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.lib.reflecttools.bridge.MethodBridge.BridgeOriginal;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.backend.InputManager;
import com.spazedog.xposed.additionsgb.backend.pwm.EventManager.State;
import com.spazedog.xposed.additionsgb.backend.pwm.iface.IEventMediator.ActionType;
import com.spazedog.xposed.additionsgb.backend.pwm.iface.IMediatorSetup.ORIGINAL;
import com.spazedog.xposed.additionsgb.backend.pwm.iface.IMediatorSetup.SDK;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager.XServiceBroadcastListener;

import java.lang.reflect.Member;

public final class PhoneWindowManager {
	public static final String TAG = PhoneWindowManager.class.getName();
	
	private XServiceManager mXServiceManager;
	private EventManager mEventManager;
	
	private Boolean mInterceptKeyCode = false;
	
	private Boolean mActiveQueueing = false;
	private Boolean mActiveDispatching = false;
	
	private final Object mQueueLock = new Object();

    private volatile int mForceDispatchPass = 0;

	/**
	 * This is a static initialization method.
	 */
	public static void init() {
		ReflectClass pwm = null;

		Log.i(TAG, "Instantiating Remap Engine", null);
		
		try {
			/*
			 * It has been quite consistent for many years,
			 * but in Marshmallow it finally got moved to the server namespace where it belongs.
			 */
			pwm = VERSION.SDK_INT > VERSION_CODES.LOLLIPOP_MR1 ? ReflectClass.fromName("com.android.server.policy.PhoneWindowManager")
                                                               : ReflectClass.fromName("com.android.internal.policy.impl.PhoneWindowManager");

			PhoneWindowManager instance = new PhoneWindowManager();
			
			/*
			 * Hook the init method of the PhoneWindowManager class.
			 * This is our entry to it's process. It will be 
			 * the first thing to be invoked once the system is ready
			 * to use it. 
			 */
			pwm.bridge("init", instance.hook_init);
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
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
	private final MethodBridge hook_init = new MethodBridge() {
		@Override
		public void bridgeEnd(BridgeParams params) {
            Log.i(TAG, "Setting up boot completed receiver", null);

			final Object receiver = params.receiver;

			/*
			 * Some Android services and such will not be ready yet.
			 * Let's wait until everything is up and running. 
			 */
			((Context) params.args[0]).registerReceiver(
				new BroadcastReceiver() {
					@Override
					public void onReceive(Context context, Intent intent) {
                        Log.i(TAG, "Configuring Remap Engine", null);

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
								pwm = ReflectClass.fromReceiver(receiver);
								mEventManager = new EventManager(pwm, mXServiceManager);

								if (mEventManager.isReady()) {
									/*
									 * Add the remaining PhoneWindowManager hooks
									 */
									pwm.bridge("interceptKeyBeforeQueueing", hook_interceptKeyBeforeQueueing);
									pwm.bridge("interceptKeyBeforeDispatching", hook_interceptKeyBeforeDispatching);
									pwm.bridge("performHapticFeedbackLw", hook_performHapticFeedbackLw);

									if (SDK.SAMSUNG_FEEDBACK_VERSION > 0) {
										ReflectClass spwm = ReflectClass.fromName("com.android.internal.policy.impl.sec.SamsungPhoneWindowManager");
										spwm.bridge("performSystemKeyFeedback", hook_performHapticFeedbackLw);
									}

									/*
									 * Add hooks to the ViewConfiguration class for this process,
									 * allowing us to control key timeout values that will affect the original class.
									 */
									ReflectClass wc = ReflectClass.fromName("android.view.ViewConfiguration");

									wc.bridge("getLongPressTimeout", hook_viewConfigTimeouts);
									wc.bridge("getGlobalActionKeyTimeout", hook_viewConfigTimeouts);
									
									/*
									 * Add listener to receive broadcasts from the XService
									 */
									mXServiceManager.addBroadcastListener(listener_XServiceBroadcast);
								}

							} catch (Throwable e) {
								Log.e(TAG, e.getMessage(), e);
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
	private final MethodBridge hook_viewConfigTimeouts = new MethodBridge() {
		@Override
        public void bridgeEnd(BridgeParams params) {
			if (mEventManager.hasState(State.REPEATING) && mEventManager.isDownEvent()) {
                params.setResult(10);
			}
		}
	};
	
	/**
	 * This hook is used to make all of the preparations of key handling
	 * 
	 * Original Arguments
	 * 		- Gingerbread: PhoneWindowManager.interceptKeyBeforeQueueing(Long whenNanos, Integer action, Integer flags, Integer keyCode, Integer scanCode, Integer policyFlags, Boolean isScreenOn)
	 * 		- ICS & Above: PhoneWindowManager.interceptKeyBeforeQueueing(KeyEvent event, Integer policyFlags, Boolean isScreenOn)
	 * 		- Lollipop & Above PhoneWindowManager.interceptKeyBeforeQueueing(KeyEvent event, Integer policyFlags)
	 */
	protected final MethodBridge hook_interceptKeyBeforeQueueing = new MethodBridge() {
		@Override
        public void bridgeBegin(BridgeParams params) {
			Integer methodVersion = SDK.METHOD_INTERCEPT_VERSION;
			KeyEvent keyEvent = methodVersion == 1 ? null : (KeyEvent) params.args[0];
			Integer keyCode = (Integer) (methodVersion == 1 ? params.args[3] : keyEvent.getKeyCode());
			Object keyObject = keyEvent == null ? keyCode : keyEvent;
			Integer action = (Integer) (methodVersion == 1 ? params.args[1] : keyEvent.getAction());
            Integer keyFlags = keyEvent == null ? (Integer) params.args[2] : keyEvent.getFlags();
			Integer policyFlags = (Integer) (methodVersion == 1 ? params.args[5] : params.args[1]);
			Integer policyFlagsPos = methodVersion == 1 ? 5 : 1;
			Integer repeatCount = (Integer) (methodVersion == 1 ? 0 : keyEvent.getRepeatCount());
			Integer metaState = (Integer) (methodVersion == 1 ? 0 : keyEvent.getMetaState());
			Boolean isScreenOn = true;
			Boolean down = action == KeyEvent.ACTION_DOWN;
			String tag = TAG + "#Queueing/" + (down ? "Down " : "Up ") + keyCode + "(" + mEventManager.getTapCount() + "," + repeatCount+ "):";
			
			Long downTime = methodVersion == 1 ? (((Long) params.args[0]) / 1000) / 1000 : keyEvent.getDownTime();
			Long eventTime = android.os.SystemClock.uptimeMillis();
			
			if (android.os.Build.VERSION.SDK_INT >= 21) {
				isScreenOn = (policyFlags & ORIGINAL.FLAG_INTERACTIVE) != 0;
				
			} else {
				isScreenOn = (Boolean) (methodVersion == 1 ? params.args[6] : params.args[2]);
			}
			
			if (down && mEventManager.getKeyCount() > 0) {
				try {
					Thread.sleep(1);
					
				} catch (InterruptedException e) {}
			}
			
			synchronized(mQueueLock) {
				/*
				 * Only disable default haptic feedback on 
				 * our own injected events
				 */
				mActiveQueueing = (keyFlags & EventKey.FLAG_CUSTOM) != 0;
				
				// android.os.SystemClock.uptimeMillis
				
				/*
				 * Sent from native code and did not pass through our InputManager hook.
				 * We cannot trust the policy flags in the next method (KitKat+ issue)
				 */
                if ((policyFlags & ORIGINAL.FLAG_INJECTED) == ORIGINAL.FLAG_INJECTED
                        && (keyFlags & InputManager.FLAG_INJECTED) != InputManager.FLAG_INJECTED) {

                    mForceDispatchPass = keyCode;

                    return;

                } else if (mForceDispatchPass == keyCode) {
                    mForceDispatchPass = 0;
                }

				Boolean isInjected = (keyFlags & InputManager.FLAG_INJECTED) == InputManager.FLAG_INJECTED;
				
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
                        params.setResult(ORIGINAL.QUEUEING_ALLOW);
						
					} else if ((policyFlags & ORIGINAL.FLAG_INJECTED) != 0) {
                        /*
                         * Some ROM's disables features on injected keys. So let's remove the flag.
                         */
                        params.args[policyFlagsPos] = policyFlags & ~ORIGINAL.FLAG_INJECTED;
					}
					
				/*
				 * No need to do anything if the settings part of the module
				 * has asked for the keys. However, do make sure that the screen is on.
				 * The display could have been auto turned off while in the settings remap part.
				 * We don't want to create a situation where users can't turn the screen back on.
				 */
				} else if (mInterceptKeyCode && isScreenOn) {
					if (down) {
						/*
						 * Temp. re-activate our hooked feedback to account for ART XposedBridge being broken and does not
						 * properly invoke original methods when being asked to. It still executes the hook as well. 
						 */
						mActiveQueueing = false;
						mEventManager.performHapticFeedback(keyObject, HapticFeedbackConstants.VIRTUAL_KEY, policyFlags);
						mActiveQueueing = true;
						
					} else if (mEventManager.validateDeviceType(keyObject)) {
						Bundle bundle = new Bundle();
						bundle.putInt("keyCode", keyCode);
						
						/*
						 * Send the key back to the settings part
						 */
						mXServiceManager.sendBroadcast("keyIntercepter:keyCode", bundle);
					}

                    params.setResult(ORIGINAL.QUEUEING_REJECT);
					
				} else if (mEventManager.validateDeviceType(keyObject)) {
					/*
					 * Most ROM reboots after holding Power for 8-12s.
					 * For those missing (like Omate TrueSmart) this is kind of a replacement.
					 */
					mEventManager.powerHardResetTimer(keyCode, down);
					
					if (mEventManager.registerKey(keyCode, down, isScreenOn, keyFlags, policyFlags, metaState, downTime, eventTime)) {
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
						mActiveQueueing = false;
						mEventManager.performHapticFeedback(keyObject, HapticFeedbackConstants.VIRTUAL_KEY, policyFlags);
						mActiveQueueing = true;
					}
					
					if(Common.debug()) Log.d(tag, "Parsing the event to the queue (" + mEventManager.mState.name() + ")");

                    params.setResult(ORIGINAL.QUEUEING_ALLOW);
				}
			}
		}
		
		@Override
        public void bridgeEnd(BridgeParams params) {
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
	protected MethodBridge hook_interceptKeyBeforeDispatching = new MethodBridge() {
		@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
		@Override
        public void bridgeBegin(BridgeParams params) {
			Integer methodVersion = SDK.METHOD_INTERCEPT_VERSION;
			KeyEvent keyEvent = methodVersion == 1 ? null : (KeyEvent) params.args[1];
			Integer keyCode = (Integer) (methodVersion == 1 ? params.args[3] : keyEvent.getKeyCode());
			Integer action = (Integer) (methodVersion == 1 ? params.args[1] : keyEvent.getAction());
            Integer keyFlags = keyEvent == null ? (Integer) params.args[2] : keyEvent.getFlags();
			Integer policyFlags = (Integer) (methodVersion == 1 ? params.args[7] : params.args[2]);
			Integer policyFlagsPos = methodVersion == 1 ? 7 : 2;
			Integer repeatCount = (Integer) (methodVersion == 1 ? params.args[6] : keyEvent.getRepeatCount());
			Boolean down = action == KeyEvent.ACTION_DOWN;
			EventKey key = mEventManager.getKey(keyCode);
			String tag = TAG + "#Dispatching/" + (down ? "Down " : "Up ") + keyCode + "(" + mEventManager.getTapCount() + "," + repeatCount+ "):";

            if (mForceDispatchPass == keyCode) {
                return;
            }
			
			/*
			 * Only disable default haptic feedback on 
			 * our own injected events
			 */
			mActiveDispatching = (keyFlags & EventKey.FLAG_CUSTOM) != 0;
			
			/*
			 * Using KitKat work-around from the InputManager Hook
			 */
			Boolean isInjected = (keyFlags & InputManager.FLAG_INJECTED) == InputManager.FLAG_INJECTED;
			
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
                    params.args[policyFlagsPos] = policyFlags & ~ORIGINAL.FLAG_INJECTED;
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

                                    params.setResult(ORIGINAL.DISPATCHING_ALLOW);
									
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
								
								if (!mEventManager.handleKeyAction(eventAction, ActionType.CLICK, mEventManager.getTapCount(), mEventManager.isScreenOn(), mEventManager.isCallButton(), mEventManager.getEventTime(), mEventManager.getTapCount() == 0 ? key.getPolicyFlags() : 0)) {
									key.invokeAndRelease();
								}
							}
						}
					}
					
				} else if (!down) {
					key.release();
				}
				
				if(Common.debug()) Log.d(tag, "Disabling default dispatching (" + mEventManager.mState.name() + ")");

                params.setResult(ORIGINAL.DISPATCHING_REJECT);
				
			} else if (Common.debug()) {
				Log.d(tag, "This key is not handled by the module");
			}
		}
		
		@Override
        public void bridgeEnd(BridgeParams params) {
			mActiveDispatching = false;
		}
	};
	
	protected MethodBridge hook_performHapticFeedbackLw = new MethodBridge() {
		@Override
        public void bridgeBegin(BridgeParams params) {
			if (mActiveQueueing || mActiveDispatching) {
				if (params.method.getName().equals("performSystemKeyFeedback") || (Integer) params.args[1] == HapticFeedbackConstants.VIRTUAL_KEY) {
                    params.setResult(true);
				}
			}
		}

        @Override
        public void bridgeAttached(Member member, BridgeOriginal original) {
            if (member.getName().equals("performSystemKeyFeedback")) {
                mEventManager.addQuickWorkaroundSamsung(original);

            } else {
                mEventManager.addQuickWorkaround(original);
            }
        }
    };
}
