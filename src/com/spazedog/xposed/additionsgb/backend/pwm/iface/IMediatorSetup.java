package com.spazedog.xposed.additionsgb.backend.pwm.iface;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.KeyEvent;

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

public abstract class IMediatorSetup {
	
	public final String TAG = getClass().getName();
	
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
		
		/*
		 * 
		 */
		public static final Integer HARDWARE_CAMERA_VERSION = android.os.Build.VERSION.SDK_INT >= 11 ? 2 :1;
	}
	
	/**
	 * A class containing values from original properties
	 */
	public static final class ORIGINAL {
		public static Integer FLAG_INJECTED;
		public static Integer FLAG_VIRTUAL;
		public static Integer FLAG_WAKE_DROPPED;
		
		public static Integer QUEUEING_ALLOW;
		public static Integer QUEUEING_REJECT;
		
		public static Object DISPATCHING_ALLOW;
		public static Object DISPATCHING_REJECT;
		
		public static Integer INPUT_MODE_ASYNC;
	}
	
	protected Handler mHandler;
	
	protected WakeLock mWakelock;
	
	protected Intent mTorchIntent;
	
	protected XServiceManager mXServiceManager;
	
	protected ReflectClass mContext;
	protected ReflectClass mPhoneWindowManager; 					// com.android.internal.policy.impl.PhoneWindowManager
	protected ReflectClass mSamsungPhoneWindowManager; 				// com.android.internal.policy.impl.sec.SamsungPhoneWindowManager
	protected ReflectClass mWindowManagerService;					// android.view.IWindowManager (com.android.server.wm.WindowManagerService)
	protected ReflectClass mKeyguardMediator;						// com.android.internal.policy.impl.keyguard.KeyguardServiceDelegate or com.android.internal.policy.impl.KeyguardViewMediator
	protected ReflectClass mActivityManager;						// android.app.ActivityManager
	protected ReflectClass mActivityManagerService;					// android.app.IActivityManager (android.app.ActivityManagerNative)
	protected ReflectClass mPowerManager;							// android.os.PowerManager
	protected ReflectClass mPowerManagerService;					// android.os.IPowerManager (com.android.server.power.PowerManagerService)
	protected ReflectClass mInputManager;							// android.hardware.input.InputManager
	protected ReflectClass mAudioManager;
	protected ReflectClass mRecentApplicationsDialog;				// com.android.internal.policy.impl.RecentApplicationsDialog or com.android.internal.statusbar.IStatusBarService
	
	protected Boolean mReady = false;
	
	protected Map<String, ReflectConstructor> mConstructors = new HashMap<String, ReflectConstructor>();
	protected Map<String, ReflectMethod> mMethods = new HashMap<String, ReflectMethod>();
	protected Map<String, ReflectField> mFields = new HashMap<String, ReflectField>();
	
	protected IMediatorSetup(ReflectClass pwm, XServiceManager xServiceManager) {
		mXServiceManager = xServiceManager;
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
						recentAppsService = member.getReflectClass().newInstance(((Context) mContext.getReceiver()));
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
			if (SDK.HARDWARE_CAMERA_VERSION > 1) {
				if(Common.debug()) Log.d(TAG + "$torchLocator()", "Using native Torch service");
				
				mTorchIntent = new Intent();
				mTorchIntent.setClassName(Common.PACKAGE_NAME, Common.PACKAGE_NAME + ".ServiceTorch");
				mTorchIntent.setAction(Common.TORCH_INTENT_ACTION);
			}
			
			new Thread() {
				@Override
				public void run() {
					PackageManager pm = ((Context) mContext.getReceiver()).getPackageManager();
					List<PackageInfo> packages = pm.getInstalledPackages(0);
					
					for (PackageInfo pkg : packages) {
						Intent intent = new Intent(pkg.packageName + ".TOGGLE_FLASHLIGHT");
						List<ResolveInfo> recievers = pm.queryBroadcastReceivers(intent, 0);
						
						if (recievers.size() > 0) {
							mTorchIntent = intent; 
							mXServiceManager.putBoolean("variable:remap.support.torch", true);
							
							break;
						}
					}
				}
				
			}.start();
			
		} finally {
			mXServiceManager.putBoolean("variable:remap.support.torch", mTorchIntent != null);
		}
	}
}
