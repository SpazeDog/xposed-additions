package com.spazedog.xposed.additionsgb.backend.pwm;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dinglisch.android.tasker.TaskerIntent;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
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
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
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
import com.spazedog.lib.reflecttools.utils.ReflectConstants.Match;
import com.spazedog.lib.reflecttools.utils.ReflectException;
import com.spazedog.lib.reflecttools.utils.ReflectMember;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Settings;

public final class Mediator {
	public static final String TAG = Mediator.class.getName();
	
	public static enum ActionType { CLICK, TAP, PRESS }
	public static enum StackAction { EXLUDE_HOME, INCLUDE_HOME, JUMP_HOME }
	
	/**
	 * A class containing different feature versions based on Gingerbread and up.
	 * If a method or feature has changed since Gingerbread, the version is bumped up to the amount of 
	 * changes from Gingerbread and to the current running Android version. For an example 
	 * the parameters in the intercept methods was changed in API 11. So for API 11 and up, we assign
	 * the version as 2.
	 */
	public static final class SDK {
		private static Integer calcSamsungAPI() {
			ReflectClass spwm = ReflectClass.forName("com.android.internal.policy.impl.sec.SamsungPhoneWindowManager", Match.SUPPRESS);
			
			if (spwm.exists()) {
				return spwm.findMethod("performSystemKeyFeedback", Match.SUPPRESS, KeyEvent.class).exists() ? 1 : 
					spwm.findMethod("performSystemKeyFeedback", Match.SUPPRESS, KeyEvent.class, Boolean.TYPE, Boolean.TYPE).exists() ? 2 : 0;
			}
			
			return 0;
		}
		
		private static Integer calcInputDeviceAIP() {
			ReflectClass id = ReflectClass.forName("android.view.InputDevice", Match.SUPPRESS);
			
			if (id.exists() && id.findMethod("isExternal", Match.SUPPRESS, KeyEvent.class).exists()) {
				return 2;
			}
			
			return 1;
		}
		
		/*
		 * In Jellybean Google added a new method for checking whether a device is external or internal.
		 * For some reason they have made this small method hidden, so we need reflection to use it. 
		 */
		public static final Integer INPUT_DEVICESTORAGE_VERSION = calcInputDeviceAIP();
		
		/*
		 * Newer Samsung devices uses an internal haptic feedback method with hardcoded keycodes. 
		 * At the same time, they have removed the virtual policy flag, so we need to use their method
		 * in order to get proper haptic control. 
		 * There are two different version of this method with different parameter numbers. 
		 * In version 2, the last one needs to be be true in order to allow haptic feedback. 
		 */
		public static final Integer SAMSUNG_FEEDBACK_VERSION = calcSamsungAPI();
		
		/*
		 * Parameter change in PhoneWindowManager.interceptKeyBefore* in API 11
		 */
		public static final Integer METHOD_INTERCEPT_VERSION = android.os.Build.VERSION.SDK_INT >= 11 ? 2 : 1;
		
		/*
		 * Input management was moved from the Window Manager Service into it's own Input Manager class in API 16.
		 */
		public static final Integer MANAGER_HARDWAREINPUT_VERSION = android.os.Build.VERSION.SDK_INT >= 16 ? 2 : 1;
		
		/*
		 * In API 19 Android switched from the KeyguardMediator class to a new KeyguardDelegate class.
		 */
		public static final Integer MANAGER_KEYGUARD_VERSION = android.os.Build.VERSION.SDK_INT >= 19 ? 2 : 1;
		
		/*
		 * New tools to turn on the screen was added to the documented part in API 17.
		 * In older versions it can only be done using forceUserActivityLocked() from the PowerManagerService using reflection.
		 */
		public static final Integer MANAGER_POWER_VERSION = android.os.Build.VERSION.SDK_INT >= 17 ? 2 : 1;
		
		/*
		 * Some of the character map values are missing in API below 11, such as VirtualKey for an example.
		 */
		public static final Integer INPUT_CHARACTERMAP_VERSION = android.os.Build.VERSION.SDK_INT >= 11 ? 2 : 1;
		
		/*
		 * Multi users was not available until API 17
		 */
		public static final Integer MANAGER_MULTIUSER_VERSION = android.os.Build.VERSION.SDK_INT >= 17 ? 1 :0;
		
		/*
		 * This one got it's own service in API 11
		 */
		public static final Integer MANAGER_RECENT_DIALOG_VERSION = android.os.Build.VERSION.SDK_INT >= 11 ? 2 :1;
		
		/*
		 * Before API 11, we did not have tools like freezeRotation
		 */
		public static final Integer MANAGER_ROTATION_VERSION = android.os.Build.VERSION.SDK_INT >= 11 ? 2 :1;
		
		/*
		 * More tools like bringToFront was added in API 11
		 */
		public static final Integer MANAGER_ACTIVITY_VERSION = android.os.Build.VERSION.SDK_INT >= 11 ? 2 :1;
		
		/*
		 * ViewConfiguration.getKeyRepeatDelay() was not available until API 12
		 */
		public static final Integer VIEW_CONFIGURATION_VERSION = android.os.Build.VERSION.SDK_INT >= 12 ? 2 :1;
	}
	
	/**
	 * A class containing values from original properties
	 */
	public static final class ORIGINAL {
		public static Integer FLAG_INJECTED;
		public static Integer FLAG_VIRTUAL;
		public static Integer FLAG_WAKE;
		public static Integer FLAG_WAKE_DROPPED;
		
		public static Integer QUEUEING_ALLOW;
		public static Integer QUEUEING_REJECT;
		
		public static Object DISPATCHING_ALLOW;
		public static Object DISPATCHING_REJECT;
		
		public static Integer INPUT_MODE_ASYNC;
	}
	
	protected Handler mHandler;
	
	protected WakeLock mWakelock;
	
	private Intent mTorchIntent;
	private Boolean mTorchReceiverSet = false;
	
	private final Object mtorchLocatorLock = new Object();
	
	private XServiceManager mXServiceManager;
	
	private ReflectClass mContext;
	private ReflectClass mPhoneWindowManager; 					// com.android.internal.policy.impl.PhoneWindowManager
	private ReflectClass mSamsungPhoneWindowManager; 			// com.android.internal.policy.impl.sec.SamsungPhoneWindowManager
	private ReflectClass mWindowManagerService;					// android.view.IWindowManager (com.android.server.wm.WindowManagerService)
	private ReflectClass mKeyguardMediator;						// com.android.internal.policy.impl.keyguard.KeyguardServiceDelegate or com.android.internal.policy.impl.KeyguardViewMediator
	private ReflectClass mActivityManager;						// android.app.ActivityManager
	private ReflectClass mActivityManagerService;				// android.app.IActivityManager (android.app.ActivityManagerNative)
	private ReflectClass mPowerManager;							// android.os.PowerManager
	private ReflectClass mPowerManagerService;					// android.os.IPowerManager (com.android.server.power.PowerManagerService)
	private ReflectClass mInputManager;							// android.hardware.input.InputManager
	private ReflectClass mAudioManager;
	private ReflectClass mRecentApplicationsDialog;	// com.android.internal.policy.impl.RecentApplicationsDialog or com.android.internal.statusbar.IStatusBarService
	
	private Boolean mReady = false;
	
	private Map<String, ReflectConstructor> mConstructors = new HashMap<String, ReflectConstructor>();
	private Map<String, ReflectMethod> mMethods = new HashMap<String, ReflectMethod>();
	private Map<String, ReflectField> mFields = new HashMap<String, ReflectField>();
	
	protected Mediator(ReflectClass pwm, XServiceManager xManager) {
		mXServiceManager = xManager;
		mContext = pwm.findFieldDeep("mContext").getValueToInstance();
		mPhoneWindowManager = pwm;
		
		try {
			mHandler = (Handler) pwm.findFieldDeep("mHandler").getValue();
			
		} catch (ReflectException e) {
			mHandler = new Handler();
		}
		
		/*
		 * Get all needed original property values
		 */
		ReflectClass wmp = ReflectClass.forName("android.view.WindowManagerPolicy");
		
		ORIGINAL.FLAG_INJECTED = (Integer) wmp.findField("FLAG_INJECTED").getValue();
		ORIGINAL.FLAG_VIRTUAL = (Integer) wmp.findField("FLAG_VIRTUAL").getValue();
		ORIGINAL.FLAG_WAKE = (Integer) ((wmp.findField("FLAG_WAKE").getValue()));
		ORIGINAL.FLAG_WAKE_DROPPED = (Integer) ((wmp.findField("FLAG_WAKE_DROPPED").getValue()));
		
		ORIGINAL.QUEUEING_ALLOW = (Integer) wmp.findFieldDeep("ACTION_PASS_TO_USER").getValue();
		ORIGINAL.QUEUEING_REJECT = 0;
		
		ORIGINAL.DISPATCHING_ALLOW = SDK.METHOD_INTERCEPT_VERSION == 1 ? false : 0;
		ORIGINAL.DISPATCHING_REJECT = SDK.METHOD_INTERCEPT_VERSION == 1 ? true : -1;
		
		if (SDK.MANAGER_HARDWAREINPUT_VERSION > 1) {
			ORIGINAL.INPUT_MODE_ASYNC = (Integer) ReflectClass.forName("android.hardware.input.InputManager").findField("INJECT_INPUT_EVENT_MODE_ASYNC").getValue();
		}
		
		/*
		 * Get the Samsung specific haptic feedback methods
		 */
		if (SDK.SAMSUNG_FEEDBACK_VERSION > 0) {
			/*
			 * The instance of com.android.internal.policy.impl.sec.SamsungPhoneWindowManager
			 * is located at com.android.internal.policy.impl.PhoneWindowManager$mSPWM
			 */
			mSamsungPhoneWindowManager = pwm.findField("mSPWM").getValueToInstance();
			
			if (SDK.SAMSUNG_FEEDBACK_VERSION == 1) {
				mMethods.put("samsung.performSystemKeyFeedback", mSamsungPhoneWindowManager.findMethod("performSystemKeyFeedback", Match.DEFAULT, KeyEvent.class));
				
			} else {
				mMethods.put("samsung.performSystemKeyFeedback", mSamsungPhoneWindowManager.findMethod("performSystemKeyFeedback", Match.DEFAULT, KeyEvent.class, Boolean.TYPE, Boolean.TYPE));
			}
		}
		
		/*
		 * Get the regular haptic feedback method
		 */
		mMethods.put("performHapticFeedback", pwm.findMethodDeep("performHapticFeedbackLw", Match.BEST, "android.view.WindowManagerPolicy$WindowState", Integer.TYPE, Boolean.TYPE));
		
		/*
		 * Locate KeyGuard Tools
		 */
		mKeyguardMediator = pwm.findFieldDeep( SDK.MANAGER_KEYGUARD_VERSION > 1 ? "mKeyguardDelegate" : "mKeyguardMediator" ).getValueToInstance();
		mKeyguardMediator.setOnErrorListener(new OnErrorListener(){
			@Override
			public void onError(ReflectMember<?> member) {
				member.getReflectClass().setReceiver(
						mPhoneWindowManager.findField( SDK.MANAGER_KEYGUARD_VERSION > 1 ? "mKeyguardDelegate" : "mKeyguardMediator" ).getValue()
				);
			}
		});
		
		mMethods.put("KeyguardMediator.isShowing", mKeyguardMediator.findMethodDeep("isShowingAndNotHidden"));
		mMethods.put("KeyguardMediator.isLocked", mKeyguardMediator.findMethodDeep("isShowing"));
		mMethods.put("KeyguardMediator.isRestricted", mKeyguardMediator.findMethodDeep("isInputRestricted"));
		mMethods.put("KeyguardMediator.dismiss", mKeyguardMediator.findMethodDeep("keyguardDone", Match.DEFAULT, Boolean.TYPE, Boolean.TYPE));
		
		/*
		 * Get the Activity Management tools
		 */
		mActivityManager = ReflectClass.forReceiver(((Context) mContext.getReceiver()).getSystemService(Context.ACTIVITY_SERVICE));
		mActivityManagerService = ReflectClass.forName("android.app.ActivityManagerNative").findMethod("getDefault").invokeToInstance();
		
		/*
		 * Get the Power Management tools
		 */
		mPowerManager = ReflectClass.forReceiver(((Context) mContext.getReceiver()).getSystemService(Context.POWER_SERVICE));
		mPowerManagerService = mPowerManager.findField("mService").getValueToInstance();
		mWakelock = ((PowerManager) mPowerManager.getReceiver()).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HookedPhoneWindowManager");
		
		if (SDK.MANAGER_POWER_VERSION == 1) {
			mMethods.put("forceUserActivityLocked", mPowerManagerService.findMethodDeep("forceUserActivityLocked"));
		}
		
		/*
		 * Get Input Injection tools
		 */
		mWindowManagerService = pwm.findFieldDeep("mWindowManager").getValueToInstance();
		
		if (SDK.MANAGER_HARDWAREINPUT_VERSION > 1) {
			mInputManager = ReflectClass.forName("android.hardware.input.InputManager").findMethod("getInstance").invokeForReceiver();
			mMethods.put("injectInputEvent", mInputManager.findMethodDeep("injectInputEvent", Match.DEFAULT, KeyEvent.class, Integer.TYPE));
		
		} else {
			mMethods.put("injectInputEvent", mWindowManagerService.findMethodDeep("injectInputEventNoWait", Match.DEFAULT, KeyEvent.class));
		}
		
		/*
		 * Get a hidden method to check internal/external state of devices
		 */
		if (SDK.INPUT_DEVICESTORAGE_VERSION > 1) {
			mMethods.put("isDeviceExternal", ReflectClass.forName("android.view.InputDevice").findMethod("isExternal"));
		}
		
		/*
		 * Get Audio tools
		 */
		mAudioManager = ReflectClass.forReceiver(((Context) mContext.getReceiver()).getSystemService(Context.AUDIO_SERVICE));
		
		/*
		 * Get Multi User tools
		 */
		if (SDK.MANAGER_MULTIUSER_VERSION > 0) {
			mConstructors.put("UserHandle", ReflectClass.forName("android.os.UserHandle").findConstructor(Match.BEST, Integer.TYPE));
			mFields.put("UserHandle.current", ReflectClass.forName("android.os.UserHandle").findField("USER_CURRENT"));
			mMethods.put("startActivityAsUser", mContext.findMethodDeep("startActivityAsUser", Match.BEST, Intent.class, "android.os.UserHandle"));
			mMethods.put("sendBroadcastAsUser", mContext.findMethodDeep("sendBroadcastAsUser", Match.BEST, Intent.class, "android.os.UserHandle"));
		}
		
		/*
		 * Get Tools for displaying Global Actions Menu
		 */
		try {
			mMethods.put("closeSystemDialogs", mActivityManagerService.findMethodDeep("closeSystemDialogs", Match.BEST, String.class));
			
			try {
				mMethods.put("showGlobalActionsDialog", mPhoneWindowManager.findMethodDeep("showGlobalActionsDialog"));
				mXServiceManager.putBoolean("variable:remap.support.global_actions", true);
				
			} catch (ReflectException e) {
				try {
					/*
					 * Support for ROM's like SlimKat that uses a 'boolean pokeWakeLock' parameter
					 */
					mMethods.put("showGlobalActionsDialog.custom", mPhoneWindowManager.findMethodDeep("showGlobalActionsDialog", Match.BEST, Boolean.TYPE));
					mXServiceManager.putBoolean("variable:remap.support.global_actions", true);
					
				} catch (ReflectException ei) {
					if(Common.debug()) Log.d(TAG, "Missing PhoneWindowManager.showGlobalActionsDialog()");
				}
			}
			
			mRecentApplicationsDialog = ReflectClass.forName( SDK.MANAGER_RECENT_DIALOG_VERSION > 1 ? "com.android.internal.statusbar.IStatusBarService" : "com.android.internal.policy.impl.RecentApplicationsDialog" );
			mRecentApplicationsDialog.setOnReceiverListener(new OnReceiverListener(){
				@Override
				public Object onReceiver(ReflectMember<?> member) {
					Object recentAppsService;
					
					if (SDK.MANAGER_RECENT_DIALOG_VERSION > 1) {
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
			
			mMethods.put("toggleRecentApps", mRecentApplicationsDialog.findMethodDeep( SDK.MANAGER_RECENT_DIALOG_VERSION > 1 ? "toggleRecentApps" : "show" ));
			mXServiceManager.putBoolean("variable:remap.support.recent_dialog", true);
			
		} catch (ReflectException e) {
			if(Common.debug()) Log.d(TAG, "Missing IActivityManager.closeSystemDialogs()");
		}
		
		/*
		 * Get ScreenShot Tools
		 */
		try {
			/*
			 * This does not exists in all Gingerbread versions
			 */
			mMethods.put("takeScreenshot", mPhoneWindowManager.findMethodDeep("takeScreenshot"));
			mXServiceManager.putBoolean("variable:remap.support.screenshot", true);
			
		} catch (ReflectException e) {}
		
		/*
		 * Get Rotation Tools
		 */
		mMethods.put("getRotation", mWindowManagerService.findMethodDeep("getRotation"));
		if (SDK.MANAGER_ROTATION_VERSION > 1) {
			mMethods.put("freezeRotation", mWindowManagerService.findMethodDeep("freezeRotation", Match.BEST, Integer.TYPE));
			mMethods.put("thawRotation", mWindowManagerService.findMethodDeep("thawRotation"));
		}
		
		/*
		 * Find tools to handle wake keys
		 */
		try {
			mMethods.put("isWakeKeyWhenScreenOff", mPhoneWindowManager.findMethodDeep("isWakeKeyWhenScreenOff", Match.BEST, Integer.TYPE));
			
		} catch (ReflectException e) {}
		
		/*
		 * Start searching for torch support
		 */
		if (((Context) mContext.getReceiver()).getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
			torchLocator();
		}
		
		/*
		 * 
		 */
		mMethods.put("forceStopPackage", mActivityManagerService.findMethodDeep("forceStopPackage", Match.BEST, SDK.MANAGER_MULTIUSER_VERSION > 0 ? new Object[]{String.class, Integer.TYPE} : new Object[]{String.class}));
		
		mReady = true;
	}
	
	public Boolean isReady() {
		return mReady;
	}
	
	protected void torchLocator() {
		(new Thread() {
			@Override
			public void run() {
				synchronized (mtorchLocatorLock) { 
					if (mTorchIntent == null) {
						if(Common.debug()) Log.d(TAG + "$torchLocator()", "Starting the search for a Torch app with Intent support");
						
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
							
							if(Common.debug()) Log.d(TAG + "$torchLocator()", "Found CyanogenMod Intent");
							
						} catch (ReflectException er) {
							if(Common.debug()) Log.d(TAG + "$torchLocator()", "No CyanogenMod Intent found. Searching in installed applications");
							
							/*
							 * Search for Torch Apps that supports <package name>.TOGGLE_FLASHLIGHT intents
							 */
							PackageManager pm = ((Context) mContext.getReceiver()).getPackageManager();
							List<PackageInfo> packages = pm.getInstalledPackages(0);
							
							for (PackageInfo pkg : packages) {
								Intent intent = new Intent(pkg.packageName + ".TOGGLE_FLASHLIGHT");
								List<ResolveInfo> recievers = pm.queryBroadcastReceivers(intent, 0);
								
								if (recievers.size() > 0) {
									if(Common.debug()) Log.d(TAG + "$torchLocator()", "Found Application Intent for " + pkg.packageName);
									
									mTorchIntent = intent; break;
								}
							}
							
							if(Common.debug() && mTorchIntent == null) Log.d(TAG + "$torchLocator()", "No Intents found in the installed applications");
							
							if (!mTorchReceiverSet) {
								mTorchReceiverSet = true;
						        IntentFilter filter = new IntentFilter();
						        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
						        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
						        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
						        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
						        filter.addDataScheme("package");
						        
						        ((Context) mContext.getReceiver()).registerReceiver(new BroadcastReceiver() {
						            @Override
						            public void onReceive(Context context, Intent intent) {
						            	if(Common.debug()) Log.d(TAG + "$torchLocator()", "Receiving Application changes. Checking new state of Torch Intents");
						            	
						            	Uri uri = intent.getData();
						            	String packageName = uri != null ? uri.getSchemeSpecificPart() : null;
						            	String action = intent.getAction();
						            	
						            	if (packageName != null && ((mTorchIntent == null && Intent.ACTION_PACKAGE_ADDED.equals(action)) || (mTorchIntent != null && packageName.equals(mTorchIntent.getPackage())))) {
						            		/*
						            		 * We can't wrap the whole block below, as the receiver and the locator are executed in different Threads.
						            		 */
						            		synchronized (mtorchLocatorLock) {}
						            		
						            		PackageManager pm = context.getPackageManager();
											Intent pkgIntent = new Intent(packageName + ".TOGGLE_FLASHLIGHT");
											List<ResolveInfo> recievers = pm.queryBroadcastReceivers(pkgIntent, 0);
											
						            		if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
						            			if (recievers.size() > 0) {
						            				if(Common.debug()) Log.d(TAG + "$torchLocator()", "Found Application Intent for newly installed " + packageName);
						            				
						            				mTorchIntent = pkgIntent;
						            				mXServiceManager.putBoolean("variable:remap.support.torch", true);
						            			}
						            			
						            		} else {
						            			if (recievers.size() == 0) {
						            				if(Common.debug()) Log.d(TAG + "$torchLocator()", "Starting a new search for Torch Intents");
						            				
						            				mTorchIntent = null;
						            				mXServiceManager.putBoolean("variable:remap.support.torch", false);
						            				torchLocator();
						            			}
						            		}
						            	}
						            }
						            
						        }, filter, null, mHandler);
							}
						}
						
						mXServiceManager.putBoolean("variable:remap.support.torch", mTorchIntent != null);
					}
				}
			}
			
		}).start();
	}
	
	/*
	 * DOTO: Make a cache based on deviceId. To much IPC communication in this one.
	 */
	public Boolean validateDeviceType(Object event) {
		/*
		 * Gingerbread has no access to the KeyEvent in the intercept method.
		 * Instead we parse the keycode on these versions and skip the first check here. 
		 */
		KeyEvent keyEvent = event instanceof KeyEvent ? (KeyEvent) event : null;
		Integer keyCode = event instanceof KeyEvent ? keyEvent.getKeyCode() : (Integer) event;
		
		if (keyEvent != null && keyEvent.getDeviceId() != -1) {
			Integer source = keyEvent.getSource();
			InputDevice device = keyEvent.getDevice();
			
			/*
			 * We do not want to handle regular Keyboards or gaming devices. 
			 * Do not trust KeyCharacterMap.getKeyboardType() as it can easily display anything
			 * as a FULL PC Keyboard. InputDevice.getKeyboardType() should be safer. 
			 */
			if ((device != null && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) || 
					(source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
					(source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD || 
					(source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
				
				return false;
			}
		}
		
		/*
		 * Now that we know that the device type is supported, let's see if we should handle external once.
		 */
		if (!mXServiceManager.getBoolean(Settings.REMAP_ALLOW_EXTERNALS)) {
			if (SDK.INPUT_DEVICESTORAGE_VERSION > 1) {
				InputDevice device = keyEvent.getDevice();
				
				try {
					/*
					 * @Google get a grip, this method should be publicly accessible. Makes no sense to hide it.
					 */
					return device == null || (Boolean) mMethods.get("isDeviceExternal").invokeReceiver(device);
					
				} catch (ReflectException e) { 
					Log.e(TAG, e.getMessage(), e);
				}
				
			} else {
				return KeyCharacterMap.deviceHasKey(keyCode);
			}
		}
		
		return true;
	}
	
	@SuppressLint("NewApi")
	protected void injectInputEvent(Object event, Integer action, Long downTime, Long eventTime, Integer repeatCount, Integer flags) {
		synchronized(PhoneWindowManager.class) {
			KeyEvent keyEvent = null;
			Integer[] actions = action == KeyEvent.ACTION_MULTIPLE ? new Integer[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP} : new Integer[]{action};
			Long time = SystemClock.uptimeMillis();
			
			if (downTime == 0L)
				downTime = time;
			
			if (eventTime == 0L)
				eventTime = time;
			
			if ((flags & KeyEvent.FLAG_FROM_SYSTEM) == 0) 
				flags |= KeyEvent.FLAG_FROM_SYSTEM;
			
			if ((flags & ORIGINAL.FLAG_INJECTED) == 0) 
				flags |= ORIGINAL.FLAG_INJECTED;
			
			if ((flags & KeyEvent.FLAG_LONG_PRESS) == 0 && repeatCount == 1) 
				flags |= KeyEvent.FLAG_LONG_PRESS;
			
			if ((flags & KeyEvent.FLAG_LONG_PRESS) != 0 && repeatCount != 1) 
				flags &= ~KeyEvent.FLAG_LONG_PRESS;
			
			if (event instanceof KeyEvent) {
				keyEvent = KeyEvent.changeTimeRepeat((KeyEvent) event, eventTime, repeatCount, flags);
				
			} else {
				keyEvent = new KeyEvent(downTime, eventTime, actions[0], (Integer) event, repeatCount, 0, (SDK.INPUT_CHARACTERMAP_VERSION > 1 ? KeyCharacterMap.VIRTUAL_KEYBOARD : 0), 0, flags, InputDevice.SOURCE_KEYBOARD);
			}
			
			for (int i=0; i < actions.length; i++) {
				/*
				 * This is for when we have both an up and down event. 
				 */
				if (keyEvent.getAction() != actions[i]) {
					keyEvent = KeyEvent.changeAction(keyEvent, actions[i]);
				}
				
				try {
					if (SDK.MANAGER_HARDWAREINPUT_VERSION > 1) {
						mMethods.get("injectInputEvent").invoke(keyEvent, ORIGINAL.INPUT_MODE_ASYNC);
						
					} else {
						mMethods.get("injectInputEvent").invoke(keyEvent);
					}
					
				} catch (ReflectException e) {
					Log.e(TAG, e.getMessage(), e);
				}	
			}
		}
	}

	protected void performHapticFeedback(KeyEvent keyEvent, Integer type, Integer policyFlags) {
		try {
			if (type == HapticFeedbackConstants.VIRTUAL_KEY) {
				if (SDK.SAMSUNG_FEEDBACK_VERSION == 1) {
					mMethods.get("samsung.performSystemKeyFeedback").invokeOriginal(keyEvent); return;
					
				} else if (SDK.SAMSUNG_FEEDBACK_VERSION == 2) {
					mMethods.get("samsung.performSystemKeyFeedback").invokeOriginal(keyEvent, false, true); return;
					
				} else if ((policyFlags & ORIGINAL.FLAG_VIRTUAL) == 0) {
					return;
				}
			}
			
			mMethods.get("performHapticFeedback").invokeOriginal(null, type, false);
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	protected void pokeUserActivity(Long time, Boolean forced) {
		if (forced) {
			if (SDK.MANAGER_POWER_VERSION > 1) {
				((PowerManager) mPowerManager.getReceiver()).wakeUp(time);
				
			} else {
				/*
				 * API's below 17 does not support PowerManager#wakeUp, so
				 * instead we will trick our way into the hidden IPowerManager#forceUserActivityLocked which 
				 * is not accessible trough the regular PowerManager class. It is the same method that 
				 * turns on the screen when you plug in your USB cable.
				 */
				try {
					mMethods.get("forceUserActivityLocked").invoke();
					
				} catch (ReflectException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
			
		} else {
			if (!mWakelock.isHeld()) {
				mWakelock.acquire(3000);
			}
			
			((PowerManager) mPowerManager.getReceiver()).userActivity(time, true);
		}
	}
	
	@SuppressLint("NewApi")
	protected void changeDisplayState(Long time, Boolean on) {
		if (on) {
			pokeUserActivity(time, true);
			
		} else {
			((PowerManager) mPowerManager.getReceiver()).goToSleep(time);
		}
	}
	
	public Boolean isKeyguardShowing() {
		try {
			return (Boolean) mMethods.get("KeyguardMediator.isShowing").invoke();
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		
		return false;
	}
	
	public Boolean isKeyguardLockedAndInsecure() {
		if (isKeyguardLocked()) {
			try {
				return !((Boolean) mMethods.get("KeyguardMediator.isRestricted").invoke());
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
		
		return false;
	}
	
	public Boolean isKeyguardLocked() {
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
	
	public ActivityManager.RunningTaskInfo getPackageFromStack(Integer stack, StackAction action) {
		List<ActivityManager.RunningTaskInfo> packages = ((ActivityManager) mActivityManager.getReceiver()).getRunningTasks(5);
		String currentHome = action != StackAction.EXLUDE_HOME ? getHomePackage() : null;
		
		for (int i=stack; i < packages.size(); i++) {
			String packageName = packages.get(i).baseActivity.getPackageName();
			
			if (!packageName.equals("com.android.systemui") && packages.get(i).id != 0) {
				if (action == StackAction.INCLUDE_HOME || !packageName.equals(currentHome)) {
					return packages.get(i);
					
				} else if (action == StackAction.JUMP_HOME) {
					continue;
				}
				
				break;
			}
		}
		
		return null;
	}
	
	public String getPackageNameFromStack(Integer stack, StackAction action) {
		ActivityManager.RunningTaskInfo pkg = getPackageFromStack(stack, action);
		
		return pkg != null ? pkg.baseActivity.getPackageName() : null;
	}
	
	public Integer getPackageIdFromStack(Integer stack, StackAction action) {
		ActivityManager.RunningTaskInfo pkg = getPackageFromStack(stack, action);
		
		return pkg != null ? pkg.id : 0;
	}
	
	protected Boolean invokeCallButton() {
		Integer mode = ((AudioManager) mAudioManager.getReceiver()).getMode();
		Integer callCode = 0;
		
		if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
			callCode = KeyEvent.KEYCODE_ENDCALL;
			
		} else if (mode == AudioManager.MODE_RINGTONE) {
			callCode = KeyEvent.KEYCODE_CALL;
		}
		
		if (callCode > 0) {
			injectInputEvent(callCode, KeyEvent.ACTION_MULTIPLE, 0L, 0L, 0, 0); return true;
		}
		
		return false;
	}
	
	public Object getUserInstance() {
		return mConstructors.get("UserHandle").invoke(
				mFields.get("UserHandle.current").getValue()
		);
	}
	
	protected void launchIntent(Intent intent) {
		if (SDK.MANAGER_MULTIUSER_VERSION > 0) {
			try {
				mMethods.get("startActivityAsUser").invoke(intent, getUserInstance());
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			
		} else {
			((Context) mContext.getReceiver()).startActivity(intent);
		}
	}
	
	protected void launchPackage(String packageName) {
		Intent intent = ((Context) mContext.getReceiver()).getPackageManager().getLaunchIntentForPackage(packageName);
		
		if (isKeyguardLockedAndInsecure()) {
			keyGuardDismiss();
		}
		
		if (intent != null) {
			intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
			
		} else {
			/*
			 * In case the app has been deleted after button setup
			 */
			intent = new Intent(Intent.ACTION_VIEW);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.setData(Uri.parse("market://details?id="+packageName));
		}
		
		launchIntent(intent);
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected void togglePreviousApplication() {
		if (SDK.MANAGER_ACTIVITY_VERSION > 1) {
			Integer packageId = getPackageIdFromStack(1, StackAction.JUMP_HOME);
			
			if (packageId > 0) {
				((ActivityManager) mActivityManager.getReceiver()).moveTaskToFront(packageId, 0);
			}
			
		} else {
			String packageName = getPackageNameFromStack(1, StackAction.JUMP_HOME);
			
			if (packageName != null) {
				launchPackage(packageName);
			}
		}
	}
	
	protected void killForegroundApplication() {
		String packageName = getPackageNameFromStack(0, StackAction.EXLUDE_HOME);
		
		if (packageName != null) {
			if(Common.debug()) Log.d(TAG, "Invoking force stop on " + packageName);
			
			try {
				if (SDK.MANAGER_MULTIUSER_VERSION > 0) {
					mMethods.get("forceStopPackage").invoke(packageName, mFields.get("UserHandle.current").getValue());

				} else {
					mMethods.get("forceStopPackage").invoke(packageName);
				}
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}
	
	protected void sendBroadcast(Intent intent) {
		if (SDK.MANAGER_MULTIUSER_VERSION > 0) {
			try {
				mMethods.get("sendBroadcastAsUser").invoke(intent, getUserInstance());
				
			} catch (ReflectException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			
		} else {
			((Context) mContext.getReceiver()).sendBroadcast(intent);
		}
	}
	
	protected void toggleFlashLight() {
		if (mTorchIntent != null) {
			sendBroadcast(mTorchIntent);
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
	
	protected void openGlobalActionsDialog() {
		if(Common.debug()) Log.d(TAG, "Invoking Global Actions Dialog");
		
		sendCloseSystemWindows("globalactions");
		
		try {
			if (mMethods.containsKey("showGlobalActionsDialog.custom")) {
				mMethods.get("showGlobalActionsDialog.custom").invoke(true);
				
			} else {
				mMethods.get("showGlobalActionsDialog").invoke();
			}
			
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
	
	protected void takeScreenshot() {
		try {
			mMethods.get("takeScreenshot").invoke();
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	protected void freezeRotation(Integer orientation) {
		if (SDK.MANAGER_ROTATION_VERSION > 1) {
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
			android.provider.Settings.System.putInt(((Context) mContext.getReceiver()).getContentResolver(), android.provider.Settings.System.ACCELEROMETER_ROTATION, orientation != 1 ? 1 : 0);
		}
	}
	
	public Boolean isRotationLocked() {
        return android.provider.Settings.System.getInt(((Context) mContext.getReceiver()).getContentResolver(), android.provider.Settings.System.ACCELEROMETER_ROTATION, 0) == 0;
	}
	
	public Integer getCurrentRotation() {
		try {
			return (Integer) mMethods.get("getRotation").invoke();

		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		
		return 0;
	}
	
	public Integer getNextRotation(Boolean backwards) {
		Integer  position = getCurrentRotation();
		
		return (position == Surface.ROTATION_90 || position == Surface.ROTATION_0) && backwards ? 270 : 
			(position == Surface.ROTATION_270 || position == Surface.ROTATION_0) && !backwards ? 90 : 0;
	}
	
	public String getHomePackage() {
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		ResolveInfo res = ((Context) mContext.getReceiver()).getPackageManager().resolveActivity(intent, 0);
		
		return res.activityInfo != null && !"android".equals(res.activityInfo.packageName) ? 
				res.activityInfo.packageName : "com.android.launcher";
	}
	
	public Boolean isWakeKeyWhenScreenOff(Integer keyCode) {
		if (mMethods.get("isWakeKeyWhenScreenOff") != null) {
			return (Boolean) mMethods.get("isWakeKeyWhenScreenOff").invoke(keyCode);
		}
		
		return true;
	}
	
	public Integer fixPolicyFlags(Integer keyCode, Integer policyFlags) {
		if (!isWakeKeyWhenScreenOff(keyCode)) {
			if ((policyFlags & ORIGINAL.FLAG_WAKE) != 0) 
				policyFlags &= ~ORIGINAL.FLAG_WAKE;
			
			if ((policyFlags & ORIGINAL.FLAG_WAKE_DROPPED) != 0) 
				policyFlags &= ~ORIGINAL.FLAG_WAKE_DROPPED;
		}
		
		return policyFlags;
	}
	
	protected Boolean handleKeyAction(final String action, final ActionType actionType, final Boolean isScreenOn, final Boolean invokeCallbutton, final Long eventDownTime, final Integer policyFlags) {		
		if (actionType != ActionType.CLICK) {
			performHapticFeedback(null, HapticFeedbackConstants.LONG_PRESS, policyFlags);
		}
		
		/*
		 * We handle display on here, because some devices has issues
		 * when executing handlers while in deep sleep. 
		 * Some times they will need a few key presses before reacting. 
		 */
		if (!isScreenOn && ((action != null && action.equals("" + KeyEvent.KEYCODE_POWER)) || (action == null && (policyFlags & (ORIGINAL.FLAG_WAKE | ORIGINAL.FLAG_WAKE_DROPPED)) != 0))) {
			changeDisplayState(eventDownTime, true); return true;
			
		} else if (invokeCallbutton && invokeCallButton()) {
			return true;
			
		} else if (action == null) {
			return false;
		}
		
		/*
		 * This should always be wrapped and sent to a handler. 
		 * If this is executed directly, some of the actions will crash with the error 
		 * -> 'Can't create handler inside thread that has not called Looper.prepare()'
		 */
		mHandler.post(new Runnable() {
			public void run() {
				String type = Common.actionType(action);
				
				if ("launcher".equals(type)) {
					launchPackage(action);
					
				} else if ("custom".equals(type)) {
					if (!"disabled".equals(action)) {
						if ("torch".equals(action)) {
							toggleFlashLight();
							
						} else if ("powermenu".equals(action)) {
							openGlobalActionsDialog();	
							
						} else if ("recentapps".equals(action)) {
							openRecentAppsDialog();
							
						} else if ("screenshot".equals(action)) {
							takeScreenshot();
							
						} else if ("flipleft".equals(action)) {
							freezeRotation( getNextRotation(true) );
							
						} else if ("flipright".equals(action)) {
							freezeRotation( getNextRotation(false) );
							
						} else if ("fliptoggle".equals(action)) {
							if (isRotationLocked()) {
								Toast.makeText((Context) mContext.getReceiver(), "Rotation has been Enabled", Toast.LENGTH_SHORT).show();
								freezeRotation(1);
								
							} else {
								Toast.makeText((Context) mContext.getReceiver(), "Rotation has been Disabled", Toast.LENGTH_SHORT).show();
								freezeRotation(-1);
							}
							
						} else if ("previousapp".equals(action)) {
							togglePreviousApplication();
							
						} else if ("killapp".equals(action)) {
							killForegroundApplication();
							
						} else if ("guarddismiss".equals(action)) {
							keyGuardDismiss();
						}
					}
					
				} else if ("tasker".equals(type)) { 
					sendBroadcast(new TaskerIntent(action.replace("tasker:", "")));
				
				} else {
					injectInputEvent(Integer.parseInt(action), KeyEvent.ACTION_MULTIPLE, eventDownTime, 0L, 0, policyFlags);
				}
			}
		});
		
		return true;
	}
}
