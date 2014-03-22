/*
 * This file is part of the Xposed Additions Project: https://github.com/spazedog/xposed-additions
 *  
 * Copyright (c) 2014 Daniel Bergløv
 *
 * Xposed Additions is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Xposed Additions is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Xposed Additions. If not, see <http://www.gnu.org/licenses/>
 */

package com.spazedog.xposed.additionsgb.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Surface;
import android.widget.Toast;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectClass.OnErrorListener;
import com.spazedog.lib.reflecttools.ReflectClass.OnReceiverListener;
import com.spazedog.lib.reflecttools.ReflectConstructor;
import com.spazedog.lib.reflecttools.ReflectField;
import com.spazedog.lib.reflecttools.ReflectMethod;
import com.spazedog.lib.reflecttools.utils.ReflectException;
import com.spazedog.lib.reflecttools.utils.ReflectMember;
import com.spazedog.lib.reflecttools.utils.ReflectMember.Match;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.Common.Index;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;

import de.robv.android.xposed.XC_MethodHook;

public class PhoneWindowManager {
	public static final String TAG = PhoneWindowManager.class.getName();
	
	public static void init() {
		try {
			if(Common.DEBUG) Log.d(TAG, "Adding Window Manager Hook");
			
			PhoneWindowManager hooks = new PhoneWindowManager();
			ReflectClass pwm = ReflectClass.forName("com.android.internal.policy.impl.PhoneWindowManager");
			
			try {
				pwm.inject(hooks.hook_constructor);
				pwm.inject("init", hooks.hook_init);
				pwm.inject("interceptKeyBeforeQueueing", hooks.hook_interceptKeyBeforeQueueing);
				pwm.inject("interceptKeyBeforeDispatching", hooks.hook_interceptKeyBeforeDispatching);
				
			} catch (ReflectException ei) {
				Log.e(TAG, ei.getMessage(), ei);
				
				pwm.removeInjections();
			}

		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	protected static Boolean SDK_NEW_POWER_MANAGER = android.os.Build.VERSION.SDK_INT >= 17;
	protected static Boolean SDK_NEW_PHONE_WINDOW_MANAGER = android.os.Build.VERSION.SDK_INT >= 11;
	protected static Boolean SDK_NEW_RECENT_APPS_DIALOG = android.os.Build.VERSION.SDK_INT >= 11;
	protected static Boolean SDK_NEW_CHARACTERMAP = android.os.Build.VERSION.SDK_INT >= 11;
	protected static Boolean SDK_HAS_HARDWARE_INPUT_MANAGER = android.os.Build.VERSION.SDK_INT >= 16;
	protected static Boolean SDK_HAS_MULTI_USER = android.os.Build.VERSION.SDK_INT >= 17;
	protected static Boolean SDK_HAS_KEYGUARD_DELEGATE = android.os.Build.VERSION.SDK_INT >= 19;
	protected static Boolean SDK_HAS_ROTATION_TOOLS = android.os.Build.VERSION.SDK_INT >= 11;

	protected static int ACTION_PASS_QUEUEING;
	protected static int ACTION_DISABLE_QUEUEING;
	
	protected static Object ACTION_PASS_DISPATCHING;
	protected static Object ACTION_DISABLE_DISPATCHING;
	
	protected static int FLAG_INJECTED;
	protected static int FLAG_VIRTUAL;
	
	protected static int INJECT_INPUT_EVENT_MODE_ASYNC;
	
	protected Context mContext;
	protected XServiceManager mPreferences;
	
	protected Handler mHandler;
	
	protected ReflectClass mPowerManager;				// android.os.PowerManager
	protected ReflectClass mPowerManagerService;		// android.os.IPowerManager (com.android.server.power.PowerManagerService)
	protected ReflectClass mWindowManagerService;		// android.view.IWindowManager (com.android.server.wm.WindowManagerService)
	protected ReflectClass mPhoneWindowManager;			// com.android.internal.policy.impl.PhoneWindowManager
	protected ReflectClass mInputManager;				// android.hardware.input.InputManager
	protected ReflectClass mActivityManager;			// android.app.ActivityManager
	protected ReflectClass mActivityManagerService;		// android.app.IActivityManager (android.app.ActivityManagerNative)
	protected ReflectClass mAudioManager;
	protected ReflectClass mKeyguardMediator;			// com.android.internal.policy.impl.keyguard.KeyguardServiceDelegate or com.android.internal.policy.impl.KeyguardViewMediator
	protected ReflectClass mRecentApplicationsDialog;	// com.android.internal.policy.impl.RecentApplicationsDialog or com.android.internal.statusbar.IStatusBarService
	
	protected boolean mReady = false;
	
	protected KeyFlags mKeyFlags = new KeyFlags();
	protected KeyConfig mKeyConfig = new KeyConfig();
	
	protected Object mLockQueueing = new Object();
	
	protected Boolean mWasScreenOn = true;
	
	protected Intent mTorchIntent;
	
	protected Map<String, ReflectConstructor> mConstructors = new HashMap<String, ReflectConstructor>();
	protected Map<String, ReflectMethod> mMethods = new HashMap<String, ReflectMethod>();
	protected Map<String, ReflectField> mFields = new HashMap<String, ReflectField>();
	
	protected void registerMembers() {
		mMethods.put("showGlobalActionsDialog", mPhoneWindowManager.findMethodDeep("showGlobalActionsDialog")); 
		mMethods.put("takeScreenshot", mPhoneWindowManager.findMethodDeep("takeScreenshot"));
		mMethods.put("performHapticFeedback", mPhoneWindowManager.findMethodDeep("performHapticFeedbackLw", Match.BEST, "android.view.WindowManagerPolicy$WindowState", Integer.TYPE, Boolean.TYPE));
		mMethods.put("forceStopPackage", mActivityManagerService.findMethodDeep("forceStopPackage", Match.BEST, SDK_HAS_MULTI_USER ? new Object[]{String.class, Integer.TYPE} : new Object[]{String.class})); 
		mMethods.put("closeSystemDialogs", mActivityManagerService.findMethodDeep("closeSystemDialogs", Match.BEST, String.class)); 
			
		/*
		 * I really don't get Google's naming schema. 'isShowingAndNotHidden' ??? 
		 * Either it is showing or it is hidden, it cannot be both.
		 */
		mMethods.put("KeyguardMediator.isShowing", mKeyguardMediator.findMethodDeep("isShowingAndNotHidden"));
		mMethods.put("KeyguardMediator.isLocked", mKeyguardMediator.findMethodDeep("isShowing"));
		mMethods.put("KeyguardMediator.isRestricted", mKeyguardMediator.findMethodDeep("isInputRestricted"));
		mMethods.put("KeyguardMediator.dismiss", mKeyguardMediator.findMethodDeep("keyguardDone", Match.BEST, Boolean.TYPE, Boolean.TYPE));
					
		mMethods.put("toggleRecentApps", mRecentApplicationsDialog.findMethodDeep( SDK_NEW_RECENT_APPS_DIALOG ? "toggleRecentApps" : "show" ));

		if (SDK_HAS_HARDWARE_INPUT_MANAGER) {
			mMethods.put("injectInputEvent", mInputManager.findMethodDeep("injectInputEvent", Match.BEST, KeyEvent.class, Integer.TYPE));
		
		} else {
			mMethods.put("injectInputEvent", mWindowManagerService.findMethodDeep("injectInputEventNoWait", Match.BEST, KeyEvent.class));
		}

		mMethods.put("getRotation", mWindowManagerService.findMethodDeep("getRotation"));
		if (SDK_HAS_ROTATION_TOOLS) {
			mMethods.put("freezeRotation", mWindowManagerService.findMethodDeep("freezeRotation", Match.BEST, Integer.TYPE));
			mMethods.put("thawRotation", mWindowManagerService.findMethodDeep("thawRotation"));
		}

		if (SDK_HAS_MULTI_USER) {
			mConstructors.put("UserHandle", ReflectClass.forName("android.os.UserHandle").findConstructor(Match.BEST, Integer.TYPE));
			mFields.put("UserHandle.current", ReflectClass.forName("android.os.UserHandle").findField("USER_CURRENT"));
			mMethods.put("startActivityAsUser", new ReflectClass(mContext).findMethodDeep("startActivityAsUser", Match.BEST, Intent.class, "android.os.UserHandle"));
		}

		if (!SDK_NEW_POWER_MANAGER) {
			mMethods.put("forceUserActivityLocked", mPowerManagerService.findMethodDeep("forceUserActivityLocked"));
		}
	}
	
	protected XC_MethodHook hook_viewConfigTimeouts = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			if (mKeyFlags.isKeyDown()) {
				param.setResult(100);
			}
		}
	};
	
	protected XC_MethodHook hook_constructor = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			try {
				if(Common.debug()) Log.d(TAG, "Handling construct of the Window Manager instance");
	
				ReflectClass wmp = ReflectClass.forName("android.view.WindowManagerPolicy");
				
				FLAG_INJECTED = (Integer) wmp.findField("FLAG_INJECTED").getValue();
				FLAG_VIRTUAL = (Integer) wmp.findField("FLAG_VIRTUAL").getValue();
				
				ACTION_PASS_QUEUEING = (Integer) wmp.findField("ACTION_PASS_TO_USER").getValue();
				ACTION_DISABLE_QUEUEING = 0;
				
				ACTION_PASS_DISPATCHING = SDK_NEW_PHONE_WINDOW_MANAGER ? 0 : false;
				ACTION_DISABLE_DISPATCHING = SDK_NEW_PHONE_WINDOW_MANAGER ? -1 : true;
						
				if (SDK_HAS_HARDWARE_INPUT_MANAGER) {
					INJECT_INPUT_EVENT_MODE_ASYNC = (Integer) ReflectClass.forName("android.hardware.input.InputManager").findField("INJECT_INPUT_EVENT_MODE_ASYNC").getValue();
				}
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
				
				ReflectClass.forName("com.android.internal.policy.impl.PhoneWindowManager").removeInjections();
			}
		}
	};
	
	protected Thread locateTorchApps = new Thread() {
		@Override
		public void run() {
			try {
				/*
				 * If the ROM has CM Torch capabilities, then use that instead. 
				 * 
				 * Some ROM's who implements some of CM's capabilities, some times changes the name of this util.cm folder to match 
				 * their name. In these cases we don't care about consistency. If you are going to borrow from others, 
				 * then make sure to keep compatibility.
				 */
				ReflectClass torchConstants = ReflectClass.forName("com.android.internal.util.cm.TorchConstants");
				mTorchIntent = new Intent((String) torchConstants.findField("ACTION_TOGGLE_STATE").getValue());
				
			} catch (ReflectException er) {
				/*
				 * Search for Torch Apps that supports <package name>.TOGGLE_FLASHLIGHT intents
				 */
				PackageManager pm = mContext.getPackageManager();
				List<PackageInfo> packages = pm.getInstalledPackages(0);
				
				for (PackageInfo pkg : packages) {
					Intent intent = new Intent(pkg.packageName + ".TOGGLE_FLASHLIGHT");
					List<ResolveInfo> recievers = pm.queryBroadcastReceivers(intent, 0);
					
					if (recievers.size() > 0) {
						mTorchIntent = intent; break;
					}
				}
			}
		}
	};
	
	/**
	 * ========================================================================================
	 * Gingerbread uses arguments init(Context, IWindowManager, LocalPowerManager)
	 * ICS uses arguments init(Context, IWindowManager, WindowManagerFuncs, LocalPowerManager)
	 * JellyBean uses arguments init(Context, IWindowManager, WindowManagerFuncs)
	 */
	protected XC_MethodHook hook_init = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			try {
				mContext = (Context) param.args[0];
				mPowerManager = new ReflectClass(mContext.getSystemService(Context.POWER_SERVICE));
				mPowerManagerService = mPowerManager.findField("mService").getValueToInstance();
				mWindowManagerService = new ReflectClass(param.args[1]);
				mPhoneWindowManager = new ReflectClass(param.thisObject);
				mActivityManager = new ReflectClass(mContext.getSystemService(Context.ACTIVITY_SERVICE));
				mAudioManager = new ReflectClass(mContext.getSystemService(Context.AUDIO_SERVICE));
				
				mHandler = new Handler();
				
				mPreferences = XServiceManager.getInstance();
				mPreferences.registerContext(mContext);
				
				if (mPreferences == null) {
					throw new ReflectException("XService has not been started", null);
				}
				
				mContext.registerReceiver(
					new BroadcastReceiver() {
						@Override
						public void onReceive(Context context, Intent intent) {
							try {
								/*
								 * It is also best to wait with this one
								 */
								mActivityManagerService = ReflectClass.forName("android.app.ActivityManagerNative").findMethod("getDefault").invokeToInstance();
								
								if (SDK_HAS_HARDWARE_INPUT_MANAGER) {
									/*
									 * This cannot be placed in hook_init because it is to soon to call InputManager#getInstance.
									 * If we do, we will get a broken IBinder which will crash both this module along
									 * with anything else trying to access the InputManager methods.
									 */
									mInputManager = ReflectClass.forName("android.hardware.input.InputManager").findMethod("getInstance").invokeForReceiver();
								}
								
								if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
									/*
									 * This could take awhile depending on the amount of apps installed. 
									 * We use a separate thread instead of the handler to avoid blocking any key events. 
									 */
									locateTorchApps.start();
								}
								
								mKeyguardMediator = mPhoneWindowManager.findField( SDK_HAS_KEYGUARD_DELEGATE ? "mKeyguardDelegate" : "mKeyguardMediator" ).getValueToInstance();
								mKeyguardMediator.setOnErrorListener(new OnErrorListener(){
									@Override
									public void onError(ReflectMember<?> member) {
										member.getReflectClass().setReceiver(
												mPhoneWindowManager.findField( SDK_HAS_KEYGUARD_DELEGATE ? "mKeyguardDelegate" : "mKeyguardMediator" ).getValue()
										);
									}
								});
								
								mRecentApplicationsDialog = ReflectClass.forName( SDK_NEW_RECENT_APPS_DIALOG ? "com.android.internal.statusbar.IStatusBarService" : "com.android.internal.policy.impl.RecentApplicationsDialog" );
								mRecentApplicationsDialog.setOnReceiverListener(new OnReceiverListener(){
									@Override
									public Object onReceiver(ReflectMember<?> member) {
										Object recentAppsService;
										
										if (SDK_NEW_RECENT_APPS_DIALOG) {
											recentAppsService = member.getReflectClass().bindInterface("statusbar").getReceiver();
											
										} else {
											recentAppsService = member.getReflectClass().newInstance(mContext);
										}
										
										member.getReflectClass().setReceiver(recentAppsService);
										
										return recentAppsService;
									}
								});
								mRecentApplicationsDialog.setOnErrorListener(new OnErrorListener(){
									@Override
									public void onError(ReflectMember<?> member) {
										member.getReflectClass().setReceiver(null);
									}
								});
								
								registerMembers();
								
								mReady = true;
								mContext.unregisterReceiver(this);
								
								try {
									/*
									 * We hook this class here because we don't want it to affect the whole system.
									 * Also we need to control when and when not to change the return value.
									 */
									ReflectClass wc = ReflectClass.forName("android.view.ViewConfiguration");

									wc.inject("getLongPressTimeout", hook_viewConfigTimeouts);
									wc.inject("getGlobalActionKeyTimeout", hook_viewConfigTimeouts);
									
								} catch (ReflectException e) {
									Log.e(TAG, e.getMessage(), e);
								}
								
							} catch (ReflectException e) {
								Log.e(TAG, e.getMessage(), e);
								
								mPhoneWindowManager.removeInjections();
							}
						}
					}, new IntentFilter("android.intent.action.BOOT_COMPLETED")
				);
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
				
				ReflectClass.forName("com.android.internal.policy.impl.PhoneWindowManager").removeInjections();
			}
		}
	};
	
	/**
	 * ========================================================================================
	 * Gingerbread uses arguments interceptKeyBeforeQueueing(Long whenNanos, Integer action, Integer flags, Integer keyCode, Integer scanCode, Integer policyFlags, Boolean isScreenOn)
	 * ICS/JellyBean uses arguments interceptKeyBeforeQueueing(KeyEvent event, Integer policyFlags, Boolean isScreenOn)
	 */
	protected XC_MethodHook hook_interceptKeyBeforeQueueing = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			/*
			 * Do nothing until the device is done booting
			 */
			if (!mReady) {
				param.setResult(ACTION_DISABLE_QUEUEING);
				
				return;
			}

			final int action = (Integer) (!SDK_NEW_PHONE_WINDOW_MANAGER ? param.args[1] : ((KeyEvent) param.args[0]).getAction());
			final int policyFlags = (Integer) (!SDK_NEW_PHONE_WINDOW_MANAGER ? param.args[5] : param.args[1]);
			final int keyCode = (Integer) (!SDK_NEW_PHONE_WINDOW_MANAGER ? param.args[3] : ((KeyEvent) param.args[0]).getKeyCode());
			final int repeatCount = (Integer) (!SDK_NEW_PHONE_WINDOW_MANAGER ? 0 : ((KeyEvent) param.args[0]).getRepeatCount());
			final boolean isScreenOn = (Boolean) (!SDK_NEW_PHONE_WINDOW_MANAGER ? param.args[6] : param.args[2]);
			final boolean down = action == KeyEvent.ACTION_DOWN;
			
			String tag = TAG + "#Queueing/" + (down ? "Down" : "Up") + ":" + keyCode;
			
			/*
			 * Using KitKat work-around from the InputManager Hook
			 */
			final boolean isInjected = SDK_HAS_HARDWARE_INPUT_MANAGER ? 
					(((KeyEvent) param.args[0]).getFlags() & FLAG_INJECTED) != 0 : (policyFlags & FLAG_INJECTED) != 0;
			
			if (isInjected) {
				if (!down || repeatCount <= 0) {
					if(Common.debug()) Log.d(tag, "Skipping injected key");
					
					if (SDK_NEW_PHONE_WINDOW_MANAGER) {
						param.args[1] = policyFlags & ~FLAG_INJECTED;
						
					} else {
						param.args[5] = policyFlags & ~FLAG_INJECTED;
					}
					
				} else {
					if(Common.debug()) Log.d(tag, "Ignoring injected repeated key");
					
					/*
					 * Normally repeated events will not continue to invoke this method. 
					 * But it seams that repeating an event using injection will. On most devices
					 * the original methods themselves seams to be handling this just fine, but a few 
					 * stock ROM's are treating these as both new and repeated events. 
					 */
					param.setResult(ACTION_PASS_QUEUEING);
				}
			
				return;
			}
			
			synchronized(mLockQueueing) {
				if(Common.debug()) Log.d(tag, (down ? "Starting" : "Stopping") + " event");
				
				boolean isVirtual = (policyFlags & FLAG_VIRTUAL) != 0;
				
				/*
				 * Some Stock ROM's has problems detecting virtual keys. 
				 * Some of them just hard codes the keys into the class. 
				 * This provides a way to force a key being detected as virtual. 
				 */
				if (down && !isVirtual) {
					List<String> forcedKeys = (ArrayList<String>) mPreferences.getStringArray(Index.array.key.forcedHapticKeys, Index.array.value.forcedHapticKeys);
					
					if (forcedKeys.contains(""+keyCode)) {
						isVirtual = true;
					}
				}
				
				if (down && isVirtual) {
					performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
				}
				
				if (isScreenOn && mKeyFlags.isDone() && mPreferences.getBoolean("intercept_keycode", false)) {
					if (down) {
						if(Common.debug()) Log.d(tag, "Intercepting key code");
						
						mPreferences.putInt("last_intercepted_keycode", keyCode, false);
						
						mKeyFlags.reset();
					}
					
					param.setResult(ACTION_DISABLE_QUEUEING);
					
				} else {
					if (down && isScreenOn != mWasScreenOn) {
						mWasScreenOn = isScreenOn;
						
						mKeyFlags.reset();
					}
					
					mKeyFlags.registerKey(keyCode, down);
					
					if (down) {
						if (!mKeyFlags.isRepeated()) {
							if(Common.debug()) Log.d(tag, "Configuring event");
							
							mWasScreenOn = isScreenOn;
							
							Integer tapDelay = mPreferences.getInt(Common.Index.integer.key.remapTapDelay, Common.Index.integer.value.remapTapDelay);
							Integer pressDelay = mPreferences.getInt(Common.Index.integer.key.remapPressDelay, Common.Index.integer.value.remapPressDelay);
							List<String> actions = null;
							
							if (!mKeyFlags.isMulti() || mKeyFlags.isExtended()) {
								String keyGroupName = mKeyFlags.getPrimaryKey() + ":" + mKeyFlags.getSecondaryKey();
								String appCondition = !isScreenOn ? null : 
									isKeyguardShowing() ? "guard" : mKeyFlags.isExtended() ? getRunningPackage() : null;
									
								actions = appCondition != null ? mPreferences.getStringArrayGroup(String.format(Index.array.groupKey.remapKeyActions_$, appCondition), keyGroupName, null) : null;
								
								if (actions == null) {
									actions = mPreferences.getStringArrayGroup(String.format(Index.array.groupKey.remapKeyActions_$, isScreenOn ? "on" : "off"), keyGroupName, null);
								}
							}
							
							String clickAction = actions != null && actions.size() > 0 ? actions.get(0) : null;
							String tapAction = actions != null && actions.size() > 1 ? actions.get(1) : null;
							String pressAction = actions != null && actions.size() > 2 ? actions.get(2) : null;
							
							mKeyConfig.registerDelays(tapDelay, pressDelay);
							mKeyConfig.registerActions(clickAction, tapAction, pressAction);
							
							if (!isScreenOn) {
								pokeUserActivity(false);
							}
						}
					}
					
					if(Common.debug()) Log.d(tag, "Parsing event to dispatcher");
					
					param.setResult(ACTION_PASS_QUEUEING);
				}
			}
		}
	};
	
	/**
	 * ========================================================================================
	 * Gingerbread uses arguments interceptKeyBeforeDispatching(WindowState win, Integer action, Integer flags, Integer keyCode, Integer scanCode, Integer metaState, Integer repeatCount, Integer policyFlags)
	 * ICS/JellyBean uses arguments interceptKeyBeforeDispatching(WindowState win, KeyEvent event, Integer policyFlags)
	 */
	protected XC_MethodHook hook_interceptKeyBeforeDispatching = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			/*
			 * Do nothing until the device is done booting
			 */
			if (!mReady) {
				param.setResult(ACTION_DISABLE_DISPATCHING);
				
				return;
			}
			
			final int keyCode = (Integer) (!SDK_NEW_PHONE_WINDOW_MANAGER ? param.args[3] : ((KeyEvent) param.args[1]).getKeyCode());
			final int action = (Integer) (!SDK_NEW_PHONE_WINDOW_MANAGER ? param.args[1] : ((KeyEvent) param.args[1]).getAction());
			final int policyFlags = (Integer) (!SDK_NEW_PHONE_WINDOW_MANAGER ? param.args[7] : param.args[2]);
			final int eventFlags = (Integer) (!SDK_NEW_PHONE_WINDOW_MANAGER ? param.args[2] : ((KeyEvent) param.args[1]).getFlags());
			final int repeatCount = (Integer) (!SDK_NEW_PHONE_WINDOW_MANAGER ? param.args[6] : ((KeyEvent) param.args[1]).getRepeatCount());
			final boolean down = action == KeyEvent.ACTION_DOWN;
			
			String tag = TAG + "#Dispatch/" + (down ? "Down" : "Up") + ":" + keyCode;
			
			/*
			 * Using KitKat work-around from the InputManager Hook
			 */
			final boolean isInjected = SDK_HAS_HARDWARE_INPUT_MANAGER ? 
					(((KeyEvent) param.args[1]).getFlags() & FLAG_INJECTED) != 0 : (policyFlags & FLAG_INJECTED) != 0;
			
			if (isInjected) {
				if (SDK_NEW_PHONE_WINDOW_MANAGER) {
					param.args[2] = policyFlags & ~FLAG_INJECTED;
					
				} else {
					param.args[7] = policyFlags & ~FLAG_INJECTED;
				}
				
				if (down && mKeyFlags.isDefaultLongPress() && mKeyFlags.isKeyDown() && keyCode == mKeyFlags.getCurrentKey()) {
					if(Common.debug()) Log.d(tag, "Repeating default long press event count " + repeatCount);
					
					injectInputEvent(keyCode, repeatCount+1);
				}
			
				return;
				
			} else if (!down && mKeyFlags.isDefaultLongPress()) {
				if(Common.debug()) Log.d(tag, "Releasing default long press event");
				
				injectInputEvent(keyCode, -1);
				
			} else if (!mKeyFlags.wasInvoked()) {
				if(Common.debug()) Log.d(tag, (down ? "Starting" : "Stopping") + " event");
				
				if (down && !mKeyFlags.isRepeated()) {
					if(Common.debug()) Log.d(tag, "Waiting for long press timeout");
					
					Boolean wasMulti = mKeyFlags.isMulti();
					Integer curDelay = 0;
					Integer pressDelay = mKeyConfig.getLongPressDelay();
							
					do {
						try {
							Thread.sleep(10);
							
						} catch (Throwable e) {}
						
						curDelay += 10;
						
					} while (mKeyFlags.isKeyDown() && keyCode == mKeyFlags.getCurrentKey() && wasMulti == mKeyFlags.isMulti() && curDelay < pressDelay);
					
					synchronized(mLockQueueing) {
						if (mKeyFlags.isKeyDown() && keyCode == mKeyFlags.getCurrentKey() && wasMulti == mKeyFlags.isMulti()) {
							if (mKeyConfig.hasLongPressAction()) {
								if(Common.debug()) Log.d(tag, "Invoking mapped long press action");
								
								performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
								handleKeyAction(mKeyConfig.getLongPressAction(), keyCode);
								
								mKeyFlags.finish();
								
							} else {
								if(Common.debug()) Log.d(tag, "Invoking default long press action");
								
								mKeyFlags.setDefaultLongPress(true);
								mKeyFlags.finish();
								
								if (mKeyFlags.isMulti()) {
									int primary = mKeyFlags.getPrimaryKey() == keyCode ? mKeyFlags.getSecondaryKey() : mKeyFlags.getPrimaryKey();
									
									injectInputEvent(primary, 0);
								}
								
								injectInputEvent(keyCode, 0); // Force trigger default long press
								
								/*
								 * The original methods will start by getting a 0 repeat event in order to prepare. 
								 * Applications that use the tracking flag will need to original, as they cannot start 
								 * tracking from an injected key. 
								 */
								param.setResult(ACTION_PASS_DISPATCHING);
		
								return;
							}
						}
					}
					
				} else if (down) {
					if(Common.debug()) Log.d(tag, "Invoking double tap action");
					
					handleKeyAction(mKeyConfig.getDoubleTapAction(), keyCode);
					
					mKeyFlags.finish();
	
				} else {
					if (mKeyConfig.hasDoubleTapAction()) {
						if(Common.debug()) Log.d(tag, "Waiting for double tap timeout");
						
						int curDelay = 0;
						
						do {
							try {
								Thread.sleep(10);
								
							} catch (Throwable e) {}
							
							curDelay += 10;
							
						} while (!mKeyFlags.isKeyDown() && curDelay < mKeyConfig.getDoubleTapDelay());
					}
					
					synchronized(mLockQueueing) {
						if (!mKeyFlags.isKeyDown() && mKeyFlags.getCurrentKey() == keyCode) {
							int callCode = 0;
							
							if (mKeyFlags.isCallButton()) {
								int mode = ((AudioManager) mAudioManager.getReceiver()).getMode();
								
								if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
									callCode = KeyEvent.KEYCODE_ENDCALL;
									
								} else if (mode == AudioManager.MODE_RINGTONE) {
									callCode = KeyEvent.KEYCODE_CALL;
								}
							}
							
							if (callCode == 0) {
								if(Common.debug()) Log.d(tag, "Invoking single click action");
								
								handleKeyAction(mKeyConfig.getClickAction(), keyCode);
								
							} else {
								if(Common.debug()) Log.d(tag, "Invoking call button");
								
								injectInputEvent(callCode);
							}
							
							mKeyFlags.finish();
						}
					}
				}
			}
			
			param.setResult(ACTION_DISABLE_DISPATCHING);
		}
	};
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	public void pokeUserActivity(Boolean forced) {
		if (forced) {
			if (SDK_NEW_POWER_MANAGER) {
				((PowerManager) mPowerManager.getReceiver()).wakeUp(SystemClock.uptimeMillis());
				
			} else {
				/*
				 * API's below 17 does not support PowerManager#wakeUp, so
				 * instead we will trick our way into the hidden IPowerManager#forceUserActivityLocked which 
				 * is not accessible trough the regular PowerManager class. It the same method that 
				 * turns on the screen when you plug in your USB cable.
				 */
				try {
					mMethods.get("forceUserActivityLocked").invoke();
					
				} catch (ReflectException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
			
		} else {
			((PowerManager) mPowerManager.getReceiver()).userActivity(SystemClock.uptimeMillis(), true);
		}
	}

	@SuppressLint("NewApi")
	protected void changeDisplayState(Boolean on) {
		if (on) {
			pokeUserActivity(true);
			
		} else {
			((PowerManager) mPowerManager.getReceiver()).goToSleep(SystemClock.uptimeMillis());
		}
	}
	
	@SuppressLint("NewApi")
	protected void injectInputEvent(final int keyCode, final int... repeat) {
		mHandler.post(new Runnable() {
			public void run() {
				synchronized(PhoneWindowManager.class) {
					long now = SystemClock.uptimeMillis();
					int characterMap = SDK_NEW_CHARACTERMAP ? KeyCharacterMap.SPECIAL_FUNCTION : 0;
					int eventType = repeat.length == 0 || repeat[0] >= 0 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
					
					int flags = repeat.length > 0 && repeat[0] == 1 ? KeyEvent.FLAG_LONG_PRESS|KeyEvent.FLAG_FROM_SYSTEM|FLAG_INJECTED : KeyEvent.FLAG_FROM_SYSTEM|FLAG_INJECTED;
					
					int repeatCount = repeat.length == 0 ? 0 : 
						repeat[0] < 0 ? 1 : repeat[0];
						
					KeyEvent event = new KeyEvent(now, now, eventType, keyCode, repeatCount, 0, characterMap, 0, flags, InputDevice.SOURCE_KEYBOARD);
					
					try {
						if (SDK_HAS_HARDWARE_INPUT_MANAGER) {
							mMethods.get("injectInputEvent").invoke(event, INJECT_INPUT_EVENT_MODE_ASYNC);
							
						} else {
							mMethods.get("injectInputEvent").invoke(event);
						}
						
						if (repeat.length == 0) {
							if (SDK_HAS_HARDWARE_INPUT_MANAGER) {
								mMethods.get("injectInputEvent").invoke(KeyEvent.changeAction(event, KeyEvent.ACTION_UP), INJECT_INPUT_EVENT_MODE_ASYNC);
								
							} else {
								mMethods.get("injectInputEvent").invoke(KeyEvent.changeAction(event, KeyEvent.ACTION_UP));
							}
						}
						
					} catch (ReflectException e) {
						Log.e(TAG, e.getMessage(), e);
					}
				}
			}
		});
	}

	protected void performHapticFeedback(Integer effectId) {
		try {
			mMethods.get("performHapticFeedback").invoke(null, effectId, false);
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	protected Boolean isKeyguardShowing() {
		try {
			return (Boolean) mMethods.get("KeyguardMediator.isShowing").invoke();
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		
		return false;
	}
	
	protected Boolean isKeyguardLockedAndInsecure() {
		if (isKeyguardLocked()) {
			try {
				return !((Boolean) mMethods.get("KeyguardMediator.isRestricted").invoke());
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
		
		return false;
	}
	
	protected Boolean isKeyguardLocked() {
		try {
			return (Boolean) mMethods.get("KeyguardMediator.isLocked").invoke();
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		
		return false;
	}
	
	protected void keyGuardDismiss() {
		if (isKeyguardLocked()) {
			try {
				mMethods.get("KeyguardMediator.dismiss").invoke(false, true);
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}
	
	protected String getRunningPackage() {
		List<ActivityManager.RunningTaskInfo> packages = ((ActivityManager) mActivityManager.getReceiver()).getRunningTasks(1);
		
		return packages.size() > 0 ? packages.get(0).baseActivity.getPackageName() : null;
	}
	
	protected String getHomePackage() {
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
		
		return res.activityInfo != null && !"android".equals(res.activityInfo.packageName) ? 
				res.activityInfo.packageName : "com.android.launcher";
	}
	
	protected void launchApplication(String packageName) {
		Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
		
		if (isKeyguardLockedAndInsecure()) {
			keyGuardDismiss();
		}
		
		if (intent != null) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			
		} else {
			/*
			 * In case the app has been deleted after button setup
			 */
			intent = new Intent(Intent.ACTION_VIEW);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.setData(Uri.parse("market://details?id="+packageName));
		}
		
		if (SDK_HAS_MULTI_USER) {
			try {
				Object userCurrent = mFields.get("UserHandle.current").getValue();
				Object user = mConstructors.get("UserHandle").invoke(userCurrent);
				
				mMethods.get("startActivityAsUser").invoke(user);
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			
		} else {
			mContext.startActivity(intent);
		}
	}
	
	protected void toggleLastApplication() {
		List<RecentTaskInfo> packages = ((ActivityManager) mActivityManager.getReceiver()).getRecentTasks(5, ActivityManager.RECENT_WITH_EXCLUDED);
		
		for (int i=1; i < packages.size(); i++) {
			String intentString = packages.get(i).baseIntent + "";
			
			int indexStart = intentString.indexOf("cmp=")+4;
		    int indexStop = intentString.indexOf("/", indexStart);
			
			String packageName = intentString.substring(indexStart, indexStop);
			
			if (!packageName.equals(getHomePackage()) && !packageName.equals("com.android.systemui")) {
				Intent intent = packages.get(i).baseIntent;
				intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				
				mContext.startActivity(intent);
			}
		}
	}
	
	protected void sendCloseSystemWindows(String reason) {
		if(Common.debug()) Log.d(TAG, "Closing all system windows");
		
		try {
			mMethods.get("closeSystemDialogs").invoke(reason);
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	protected void openRecentAppsDialog() {
		if(Common.debug()) Log.d(TAG, "Invoking Recent Application Dialog");
		
		sendCloseSystemWindows("recentapps");
		
		try {
			mMethods.get("toggleRecentApps").invoke();
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	protected void openGlobalActionsDialog() {
		if(Common.debug()) Log.d(TAG, "Invoking Global Actions Dialog");
		
		sendCloseSystemWindows("globalactions");
		
		try {
			mMethods.get("showGlobalActionsDialog").invoke();
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	protected void freezeRotation(Integer orientation) {
		if (SDK_HAS_ROTATION_TOOLS) {
			if (orientation != 1) {
				switch (orientation) {
					case 90: orientation = Surface.ROTATION_90; break;
					case 180: orientation = Surface.ROTATION_180; break;
					case 270: orientation = Surface.ROTATION_270;
				}
				
				try {
					mMethods.get("freezeRotation").invoke(orientation);
					
				} catch (ReflectException e) {
					Log.e(TAG, e.getMessage(), e);
				}
				
			} else {
				try {
					mMethods.get("thawRotation").invoke();
					
				} catch (ReflectException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
			
		} else {
			/*
			 * TODO: Find a working way for locking Gingerbread in a specific orientation
			 */
			Settings.System.putInt(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, orientation != 1 ? 1 : 0);
		}
	}
	
	protected Boolean isRotationLocked() {
        return Settings.System.getInt(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 0;
	}
	
	protected Integer getCurrentRotation() {
		try {
			return (Integer) mMethods.get("getRotation").invoke();

		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		
		return 0;
	}
	
	protected Integer getNextRotation(Boolean backwards) {
		Integer  position = getCurrentRotation();
		
		return (position == Surface.ROTATION_90 || position == Surface.ROTATION_0) && backwards ? 270 : 
			(position == Surface.ROTATION_270 || position == Surface.ROTATION_0) && !backwards ? 90 : 0;
	}
	
	protected void killForegroundApplication() {
		if(Common.debug()) Log.d(TAG, "Start searching for foreground application to kill");
		
		List<ActivityManager.RunningTaskInfo> packages = ((ActivityManager) mActivityManager.getReceiver()).getRunningTasks(5);
		
		for (int i=0; i < packages.size(); i++) {
			String packageName = packages.get(0).baseActivity.getPackageName();
			
			if (!packageName.equals(getHomePackage()) && !packageName.equals("com.android.systemui")) {
				if(Common.debug()) Log.d(TAG, "Invoking force stop on " + packageName);
				
				try {
					if (SDK_HAS_MULTI_USER) {
						mMethods.get("forceStopPackage").invoke(packageName, mFields.get("UserHandle.current").getValue());
	
					} else {
						mMethods.get("forceStopPackage").invoke(packageName);
					}
					
				} catch (ReflectException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
		}
	}
	
	protected void takeScreenshot() {
		try {
			mMethods.get("takeScreenshot").invoke();
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	protected void toggleFlashLight() {
		if (mTorchIntent != null) {
			mContext.sendBroadcast(mTorchIntent);
		}
	}
	
	protected void handleKeyAction(final String action, final Integer keyCode) {
		final Integer code = action != null && action.matches("^[0-9]+$") ? Integer.parseInt(action) : 
			action == null ? keyCode : 0;
		
		/*
		 * We handle display on here, because some devices has issues
		 * when executing handlers while in deep sleep. 
		 * Some times they will need a few key presses before reacting. 
		 */
		if (code == KeyEvent.KEYCODE_POWER && !mWasScreenOn) {
			changeDisplayState(true); return;
		}
		
		/*
		 * This should always be wrapped and sent to a handler. 
		 * If this is executed directly, some of the actions will crash with the error 
		 * -> 'Can't create handler inside thread that has not called Looper.prepare()'
		 */
		mHandler.post(new Runnable() {
			public void run() {
				String actionType = Common.actionType(action);
				
				if ("launcher".equals(actionType)) {
					launchApplication(action);
					
				} else if ("custom".equals(actionType)) {
					if (!"disabled".equals(action)) {
						if ("recentapps".equals(action)) {
							openRecentAppsDialog();
							
						} else if ("powermenu".equals(action)) {
							openGlobalActionsDialog();
							
						} else if ("flipleft".equals(action)) {
							freezeRotation( getNextRotation(true) );
							
						} else if ("flipright".equals(action)) {
							freezeRotation( getNextRotation(false) );
							
						} else if ("fliptoggle".equals(action)) {
							if (isRotationLocked()) {
								Toast.makeText(mContext, "Rotation has been Enabled", Toast.LENGTH_SHORT).show();
								freezeRotation(1);
								
							} else {
								Toast.makeText(mContext, "Rotation has been Disabled", Toast.LENGTH_SHORT).show();
								freezeRotation(-1);
							}
							
						} else if ("killapp".equals(action)) {
							killForegroundApplication();
							
						} else if ("guarddismiss".equals(action)) {
							keyGuardDismiss();
							
						} else if ("screenshot".equals(action)) {
							takeScreenshot();
							
						} else if ("torch".equals(action)) {
							toggleFlashLight();
							
						} else if ("lastapp".equals(action)) {
							toggleLastApplication();
						}
					}
					
				} else {
					injectInputEvent(code);
				}
			}
		});
	}
	
	protected class KeyConfig {
		private String mKeyActionClick = null;
		private String mKeyActionTap = null;
		private String mKeyActionPress = null;
		
		private Integer mKeyDelayTap = 0;
		private Integer mKeyDelayPress = 0;
		
		public void registerActions(String click, String tap, String press) {
			mKeyActionClick = mKeyFlags.isExtended() || (click != null && !click.contains(".")) ? click : null;
			mKeyActionTap = mKeyFlags.isExtended() || (tap != null && !tap.contains(".")) ? tap : null;
			mKeyActionPress = mKeyFlags.isExtended() || (press != null && !press.contains(".")) ? press : null;
		}
		
		public void registerDelays(Integer tap, Integer press) {
			mKeyDelayTap = tap;
			mKeyDelayPress = press;
		}
		
		public String getClickAction() {
			return mKeyActionClick;
		}
		
		public String getDoubleTapAction() {
			return mKeyActionTap;
		}
		
		public String getLongPressAction() {
			return mKeyActionPress;
		}
		
		public Boolean hasClickAction() {
			return mKeyActionClick != null;
		}
		
		public Boolean hasDoubleTapAction() {
			return mKeyActionTap != null;
		}
		
		public Boolean hasLongPressAction() {
			return mKeyActionPress != null;
		}
		
		public Integer getDoubleTapDelay() {
			return mKeyDelayTap;
		}
		
		public Integer getLongPressDelay() {
			return mKeyDelayPress;
		}
	}
	
	protected class KeyFlags {
		private Boolean mIsPrimaryDown = false;
		private Boolean mIsSecondaryDown = false;
		private Boolean mIsRepeated = false;
		private Boolean mFinished = false;
		private Boolean mReset = false;
		private Boolean mExtended = false;
		private Boolean mDefaultLongPress = false;
		private Boolean mIsCallButton = false;
		
		private Integer mPrimaryKey = 0;
		private Integer mSecondaryKey = 0;
		private Integer mCurrentKey = 0;
		
		public void finish() {
			mFinished = true;
			mReset = mSecondaryKey == 0;
		}
		
		public void reset() {
			mReset = true;
		}
		
		public void registerKey(Integer keyCode, Boolean down) {
			mCurrentKey = keyCode;

			String tag = TAG + "#KeyFlags:" + keyCode;
					
			if (down) {
				if (!isDone() && !mIsRepeated && (keyCode == mPrimaryKey || keyCode == mSecondaryKey) && mExtended) {
					if(Common.debug()) Log.d(tag, "Registring repeated event");
					
					mIsRepeated = true;
					
					if (keyCode == mSecondaryKey) {
						mIsSecondaryDown = true;
						
					} else {
						mIsPrimaryDown = true;
					}
					
				} else if (!mIsRepeated && !mReset && mPrimaryKey > 0 && mIsPrimaryDown && keyCode != mPrimaryKey && (mSecondaryKey == 0 || mSecondaryKey == keyCode)) {
					if(Common.debug()) Log.d(tag, "Registring secondary key");
					
					mIsSecondaryDown = true;
					mFinished = false;
					
					mSecondaryKey = keyCode;
					
				} else {
					if(Common.debug()) Log.d(tag, "Registring primary key");
					
					mIsPrimaryDown = true;
					mIsSecondaryDown = false;
					mIsRepeated = false;
					mFinished = false;
					mReset = false;
					mDefaultLongPress = false;
					
					mPrimaryKey = keyCode;
					mSecondaryKey = 0;
					
					mExtended = mPreferences.isPackageUnlocked();
					mIsCallButton = mPreferences.getBooleanGroup(Index.bool.key.remapCallButton, (mPrimaryKey + ":" + mSecondaryKey), Index.bool.value.remapCallButton);
				}
				
			} else {
				if (keyCode == mPrimaryKey) {
					if(Common.debug()) Log.d(tag, "Releasing primary key");
					
					mIsPrimaryDown = false;
					
					if (mIsRepeated || mSecondaryKey != 0) {
						mReset = true;
					}
					
				} else if (keyCode == mSecondaryKey) {
					if(Common.debug()) Log.d(tag, "Releasing secondary key");
					
					mIsSecondaryDown = false;
					mIsRepeated = false;
				}
			}
		}
		
		public Boolean wasInvoked() {
			return mFinished;
		}
		
		public Boolean isDone() {
			return mFinished || mReset || mPrimaryKey == 0;
		}
		
		public Boolean isMulti() {
			return mPrimaryKey > 0 && mSecondaryKey > 0;
		}
		
		public Boolean isRepeated() {
			return mIsRepeated;
		}
		
		public Boolean isKeyDown() {
			return mPrimaryKey > 0 && mIsPrimaryDown && (mSecondaryKey == 0 || mIsSecondaryDown);
		}
		
		public Boolean isExtended() {
			return mExtended;
		}
		
		public Boolean isCallButton() {
			return mIsCallButton;
		}
		
		public Boolean isDefaultLongPress() {
			return mDefaultLongPress;
		}
		
		public void setDefaultLongPress(Boolean on) {
			mDefaultLongPress = on;
		}
		
		public Integer getPrimaryKey() {
			return mPrimaryKey;
		}
		
		public Integer getSecondaryKey() {
			return mSecondaryKey;
		}
		
		public Integer getCurrentKey() {
			return mCurrentKey;
		}
	}
}
