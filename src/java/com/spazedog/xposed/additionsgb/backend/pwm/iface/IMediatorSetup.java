package com.spazedog.xposed.additionsgb.backend.pwm.iface;

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
import com.spazedog.lib.reflecttools.ReflectClass.OnClassReceiverListener;
import com.spazedog.lib.reflecttools.ReflectConstructor;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.ReflectField;
import com.spazedog.lib.reflecttools.ReflectMember.Result;
import com.spazedog.lib.reflecttools.ReflectMethod;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            try {
                ReflectClass spwm = ReflectClass.fromName("com.android.internal.policy.impl.sec.SamsungPhoneWindowManager");

                try {
                    spwm.findMethod("performSystemKeyFeedback", KeyEvent.class); return 1;

                } catch (ReflectException e) {
                    try {
                        spwm.findMethod("performSystemKeyFeedback", KeyEvent.class, Boolean.TYPE, Boolean.TYPE); return 2;

                    } catch (ReflectException ei) {
                        return 0;
                    }
                }

            } catch (ReflectException e) {
                return 0;
            }
		}
		
		private static Integer calcInputDeviceAIP() {
            try {
                ReflectClass id = ReflectClass.fromName("android.view.InputDevice");
                id.findMethod("isExternal", KeyEvent.class);

                return 2;

            } catch (ReflectException e) {
                return 1;
            }
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
		public static final Integer MANAGER_KEYGUARD_VERSION = android.os.Build.VERSION.SDK_INT < 19 ? 1 : 
			android.os.Build.VERSION.SDK_INT < 21 ? 2 : 3;
		
		/*
		 * New tools to turn on the screen was added to the documented part in API 17.
		 * In older versions it can only be done using forceUserActivityLocked() from the PowerManagerService using reflection.
		 */
		public static final Integer MANAGER_POWER_VERSION = android.os.Build.VERSION.SDK_INT < 17 ? 1 : 
			(android.os.Build.VERSION.SDK_INT < 18 ? 2 : (android.os.Build.VERSION.SDK_INT < 21 ? 3 : (android.os.Build.VERSION.SDK_INT < 23 ? 4 : 5)));
		
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
		
		public static Integer FLAG_INTERACTIVE;
		
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
		mContext = (ReflectClass) pwm.findField("mContext").getValue(Result.INSTANCE);
		mPhoneWindowManager = pwm;
		
		try {
			mHandler = (Handler) pwm.findField("mHandler").getValue();
			
		} catch (ReflectException e) {
			mHandler = new Handler();
		}
		
		/*
		 * Get all needed original property values
		 */
		ReflectClass wmp = ReflectClass.fromName("android.view.WindowManagerPolicy");
		
		ORIGINAL.FLAG_INJECTED = (Integer) wmp.findField("FLAG_INJECTED").getValue();
		ORIGINAL.FLAG_VIRTUAL = (Integer) wmp.findField("FLAG_VIRTUAL").getValue();
		ORIGINAL.FLAG_WAKE_DROPPED = (Integer) ((wmp.findField("FLAG_WAKE").getValue()));
		
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			ORIGINAL.FLAG_INTERACTIVE = (Integer) ((wmp.findField("FLAG_INTERACTIVE").getValue()));
		}
		
		ORIGINAL.QUEUEING_ALLOW = (Integer) wmp.findField("ACTION_PASS_TO_USER").getValue();
		ORIGINAL.QUEUEING_REJECT = 0;
		
		ORIGINAL.DISPATCHING_ALLOW = SDK.METHOD_INTERCEPT_VERSION == 1 ? false : 0;
		ORIGINAL.DISPATCHING_REJECT = SDK.METHOD_INTERCEPT_VERSION == 1 ? true : -1;
		
		if (SDK.MANAGER_HARDWAREINPUT_VERSION > 1) {
			ORIGINAL.INPUT_MODE_ASYNC = (Integer) ReflectClass.fromName("android.hardware.input.InputManager").findField("INJECT_INPUT_EVENT_MODE_ASYNC").getValue();
		}
		
		/*
		 * Get the Samsung specific haptic feedback methods
		 */
		if (SDK.SAMSUNG_FEEDBACK_VERSION > 0) {
			/*
			 * The instance of com.android.internal.policy.impl.sec.SamsungPhoneWindowManager
			 * is located at com.android.internal.policy.impl.PhoneWindowManager$mSPWM
			 */
			mSamsungPhoneWindowManager = (ReflectClass) pwm.findField("mSPWM").getValue(Result.INSTANCE);
			
			if (SDK.SAMSUNG_FEEDBACK_VERSION == 1) {
				mMethods.put("samsung.performSystemKeyFeedback", mSamsungPhoneWindowManager.findMethod("performSystemKeyFeedback", KeyEvent.class));
				
			} else {
				mMethods.put("samsung.performSystemKeyFeedback", mSamsungPhoneWindowManager.findMethod("performSystemKeyFeedback", KeyEvent.class, Boolean.TYPE, Boolean.TYPE));
			}
		}
		
		/*
		 * Get the regular haptic feedback method
		 */
		mMethods.put("performHapticFeedback", pwm.findMethod("performHapticFeedbackLw", "android.view.WindowManagerPolicy$WindowState", Integer.TYPE, Boolean.TYPE));
		
		/*
		 * Locate KeyGuard Tools
		 */
		mKeyguardMediator = (ReflectClass) pwm.findField( SDK.MANAGER_KEYGUARD_VERSION > 1 ? "mKeyguardDelegate" : "mKeyguardMediator" ).getValue(Result.INSTANCE);
		
		if (SDK.MANAGER_KEYGUARD_VERSION > 2) {
            try {
                mMethods.put("KeyguardMediator.isShowing", mKeyguardMediator.findMethod("isShowingAndNotOccluded"));

            } catch (ReflectException e) {
                /*
                 * TODO: Latest revision of 5.1 removed 'isShowingAndNotOccluded' from the mediator.
                 *          Only way to check this now, is by running a check on 'isShowing' along with checking some
                 *          properties in KeyguardServiceDelegate$KeyguardState. The fastest crash fix for now, is to use
                 *          the method within 'PhoneWindowManager'. We can revisit this ones the 'M' release is published,
                 *          which might need additional work as well.
                 */
                mMethods.put("KeyguardMediator.isShowing", mPhoneWindowManager.findMethod("isKeyguardShowingAndNotOccluded"));
            }
		
		} else {
			mMethods.put("KeyguardMediator.isShowing", mKeyguardMediator.findMethod("isShowingAndNotHidden"));
		}
		
		mMethods.put("KeyguardMediator.isLocked", mKeyguardMediator.findMethod("isShowing"));
		mMethods.put("KeyguardMediator.isRestricted", mKeyguardMediator.findMethod("isInputRestricted"));
		mMethods.put("KeyguardMediator.dismiss", mKeyguardMediator.findMethod("keyguardDone", Boolean.TYPE, Boolean.TYPE));
		
		/*
		 * Get the Activity Management tools
		 */
		mActivityManager = ReflectClass.fromReceiver(((Context) mContext.getReceiver()).getSystemService(Context.ACTIVITY_SERVICE));
		mActivityManagerService = (ReflectClass) ReflectClass.fromName("android.app.ActivityManagerNative").findMethod("getDefault").invoke(Result.INSTANCE);
		
		/*
		 * Get the Power Management tools
		 */
		mPowerManager = ReflectClass.fromReceiver(((Context) mContext.getReceiver()).getSystemService(Context.POWER_SERVICE));

        try {
            mPowerManagerService = (ReflectClass) mPowerManager.findField("mService").getValue(Result.INSTANCE);

        } catch (ReflectException e) {
            /*
             * This is a work-around for devices that is heavily modified, like Amazon Fire Phone where the above field for some reason returns NULL
             */
            mPowerManagerService = ReflectClass.fromName("android.os.IPowerManager").bindInterface("power");
        }

		mWakelock = ((PowerManager) mPowerManager.getReceiver()).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HookedPhoneWindowManager");
		
		/*
		 * Get Power Manager Tools
		 */
		
		if (SDK.MANAGER_POWER_VERSION > 3) {
			mMethods.put("goToSleep", mPowerManagerService.findMethod("goToSleep", Long.TYPE, Integer.TYPE, Integer.TYPE));
			
		} else if (SDK.MANAGER_POWER_VERSION > 1) { 
			mMethods.put("goToSleep", mPowerManagerService.findMethod("goToSleep", Long.TYPE, Integer.TYPE));
			
		} else {
			mMethods.put("goToSleep", mPowerManagerService.findMethod("goToSleep", Long.TYPE));
		}
		
		if (SDK.MANAGER_POWER_VERSION == 1) {
			mMethods.put("userActivity", mPowerManagerService.findMethod("userActivity", Long.TYPE, Boolean.TYPE));
			mMethods.put("forceUserActivityLocked", mPowerManagerService.findMethod("forceUserActivityLocked"));
			
		} else {
			mMethods.put("userActivity", mPowerManagerService.findMethod("userActivity", Long.TYPE, Integer.TYPE, Integer.TYPE));

            if (SDK.MANAGER_POWER_VERSION < 5) {
                mMethods.put("wakeUp", mPowerManagerService.findMethod("wakeUp", Long.TYPE));

            } else {
                mMethods.put("wakeUp", mPowerManagerService.findMethod("wakeUp", Long.TYPE, String.class, String.class));
            }
		}
		
		/*
		 * Get Input Injection tools
		 */
		mWindowManagerService = (ReflectClass) pwm.findField("mWindowManager").getValue(Result.INSTANCE);
		
		if (SDK.MANAGER_HARDWAREINPUT_VERSION > 1) {
			mInputManager = (ReflectClass) ReflectClass.fromName("android.hardware.input.InputManager").findMethod("getInstance").invoke(Result.INSTANCE);
			mMethods.put("injectInputEvent", mInputManager.findMethod("injectInputEvent", KeyEvent.class, Integer.TYPE));
		
		} else {
			mMethods.put("injectInputEvent", mWindowManagerService.findMethod("injectInputEventNoWait", KeyEvent.class));
		}
		
		/*
		 * Get a hidden method to check internal/external state of devices
		 */
		if (SDK.INPUT_DEVICESTORAGE_VERSION > 1) {
			mMethods.put("isDeviceExternal", ReflectClass.fromName("android.view.InputDevice").findMethod("isExternal"));
		}
		
		/*
		 * Get Audio tools
		 */
		mAudioManager = ReflectClass.fromReceiver(((Context) mContext.getReceiver()).getSystemService(Context.AUDIO_SERVICE));
		
		/*
		 * Get Multi User tools
		 */
		if (SDK.MANAGER_MULTIUSER_VERSION > 0) {
			mConstructors.put("UserHandle", ReflectClass.fromName("android.os.UserHandle").findConstructor(Integer.TYPE));
			mFields.put("UserHandle.current", ReflectClass.fromName("android.os.UserHandle").findField("USER_CURRENT"));
			mMethods.put("startActivityAsUser", mContext.findMethod("startActivityAsUser", Intent.class, "android.os.UserHandle"));
			mMethods.put("sendBroadcastAsUser", mContext.findMethod("sendBroadcastAsUser", Intent.class, "android.os.UserHandle"));
		}
		
		/*
		 * Get Tools for displaying Global Actions Menu
		 */
		try {
			mMethods.put("closeSystemDialogs", mActivityManagerService.findMethod("closeSystemDialogs", String.class));
			
			try {
				if (android.os.Build.VERSION.SDK_INT >= 21) {
					mMethods.put("showGlobalActionsDialog", mPhoneWindowManager.findMethod("showGlobalActions"));
					
				} else {
					mMethods.put("showGlobalActionsDialog", mPhoneWindowManager.findMethod("showGlobalActionsDialog"));
				}
				
				mXServiceManager.putBoolean("variable:remap.support.global_actions", true);
				
			} catch (ReflectException e) {
				try {
					/*
					 * Support for ROM's like SlimKat that uses a 'boolean pokeWakeLock' parameter
					 */
					mMethods.put("showGlobalActionsDialog.custom", mPhoneWindowManager.findMethod("showGlobalActionsDialog", Boolean.TYPE));
					mXServiceManager.putBoolean("variable:remap.support.global_actions", true);
					
				} catch (ReflectException ei) {
					if(Common.debug()) Log.d(TAG, "Missing PhoneWindowManager.showGlobalActionsDialog()");
				}
			}
			
			mRecentApplicationsDialog = ReflectClass.fromName(SDK.MANAGER_RECENT_DIALOG_VERSION > 1 ? "com.android.internal.statusbar.IStatusBarService" : "com.android.internal.policy.impl.RecentApplicationsDialog");
            mRecentApplicationsDialog.setReceiverListener(new OnClassReceiverListener() {
                @Override
                public Object onRequestReceiver(ReflectClass reflectClass) {
                    Object receiver = reflectClass.getReceiver();

                    if (receiver == null) {
                        if (SDK.MANAGER_RECENT_DIALOG_VERSION > 1) {
                            receiver = reflectClass.bindInterface("statusbar").getReceiver();

                        } else {
                            receiver = reflectClass.invokeConstructor(((Context) mContext.getReceiver()));
                        }

                        reflectClass.setReceiver(receiver);
                    }

                    return receiver;
                }
            });
			
			mMethods.put("toggleRecentApps", mRecentApplicationsDialog.findMethod( SDK.MANAGER_RECENT_DIALOG_VERSION > 1 ? "toggleRecentApps" : "show"));
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
			mMethods.put("takeScreenshot", mPhoneWindowManager.findMethod("takeScreenshot"));
			mXServiceManager.putBoolean("variable:remap.support.screenshot", true);
			
		} catch (ReflectException e) {}
		
		/*
		 * Get Rotation Tools
		 */
		mMethods.put("getRotation", mWindowManagerService.findMethod("getRotation"));
		if (SDK.MANAGER_ROTATION_VERSION > 1) {
			mMethods.put("freezeRotation", mWindowManagerService.findMethod("freezeRotation", Integer.TYPE));
			mMethods.put("thawRotation", mWindowManagerService.findMethod("thawRotation"));
		}
		
		/*
		 * Find tools to handle wake keys
		 */
		try {
			mMethods.put("isWakeKeyWhenScreenOff", mPhoneWindowManager.findMethod("isWakeKeyWhenScreenOff", Integer.TYPE));

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
		mMethods.put("forceStopPackage", mActivityManagerService.findMethod("forceStopPackage", SDK.MANAGER_MULTIUSER_VERSION > 0 ? new Object[]{String.class, Integer.TYPE} : new Object[]{String.class}));

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
			ReflectClass torchConstants = ReflectClass.fromName("com.android.internal.util.cm.TorchConstants");
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
