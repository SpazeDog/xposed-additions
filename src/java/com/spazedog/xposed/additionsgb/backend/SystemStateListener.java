package com.spazedog.xposed.additionsgb.backend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.ReflectMember.Match;
import com.spazedog.lib.reflecttools.ReflectMember.Result;
import com.spazedog.lib.reflecttools.ReflectMethod;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;
import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

public final class SystemStateListener {
	public static final String TAG = SystemStateListener.class.getName();
	
	public static final int STREAM_MUSIC = 3;

	private ReflectClass mAudioSystem;
	private ReflectClass mWindowManagerPolicy;
	private ReflectClass mWindowManagerService;
	private ReflectClass mActivityManagerService;
	private BackendServiceMgr mBackendMgr;
	private Context mContext;

	public static void init(Context context) {
		Utils.log(Level.INFO, TAG, "Instantiating System State Listeners");

		SystemStateListener instance = new SystemStateListener();
		
		try {
			ReflectClass activityManager = ReflectClass.fromName("com.android.server.am.ActivityManagerService");
			activityManager.bridge("systemReady", instance.systemReady);
			activityManager.bridge("setFocusedActivityLocked", instance.applicationFocusedListener);
			
			/*
			 * Multi-user support was not added until Jellybean 4.2.
			 */
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
				activityManager.bridge("dispatchUserSwitch", instance.userSwitchListener);
			}
			
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				ReflectClass.fromName("com.android.server.wm.WindowManagerService").bridge(instance.setupWindowManager);
				
			} else {
				ReflectClass.fromName("com.android.server.WindowManagerService").bridge(instance.setupWindowManager);
			}

            ReflectClass inputManager = ReflectClass.fromName("com.android.server.InputMethodManagerService");
            inputManager.bridge("showCurrentInputLocked", instance.softKeyboardListener);
            inputManager.bridge("hideCurrentInputLocked", instance.softKeyboardListener);
			
			instance.configure(context, activityManager);
			
		} catch (ReflectException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	private void configure(Context context, ReflectClass activityManager) {
		Utils.log(Level.INFO, TAG, "Starting all System State Listeners");

		mAudioSystem = ReflectClass.fromName("android.media.AudioSystem");
		mActivityManagerService = activityManager;
		mContext = context;
		
		/*
		 * Let's connect to our own service
		 */
		mBackendMgr = BackendServiceMgr.getInstance();
	}
	
	/**
	 * 
	 */
	protected MethodBridge setupWindowManager = new MethodBridge() {
        @Override
        public void bridgeEnd(BridgeParams param) {
			mWindowManagerService = ReflectClass.fromReceiver(param.receiver);
			mWindowManagerPolicy = (ReflectClass) mWindowManagerService.findField("mPolicy").getValue(Result.INSTANCE);
		}
	};
	
	/**
	 * 
	 */
	protected MethodBridge systemReady = new MethodBridge() {
        @Override
        public void bridgeEnd(BridgeParams param) {
			Utils.log(Level.INFO, TAG, "Establishing the last connections for System State Listeners");

            /*
             * Add the ActivityManagerInstance to the Receiver
             */
            mActivityManagerService.setReceiver(param.receiver);
			
			/*
			 * Add telephony listener
			 */
			TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
			telephonyManager.listen(telephonyListener, PhoneStateListener.LISTEN_CALL_STATE);
			
			/*
			 * Start the listener worker thread to monitor media events
			 */
			stateMonitor.start();
			
			/*
			 * Setup Android broadcast actions
			 */
			IntentFilter filter = new IntentFilter();
			filter.addAction(Intent.ACTION_SCREEN_ON);
			filter.addAction(Intent.ACTION_SCREEN_OFF);
			
			mContext.registerReceiver(stateBroadcastReveiver, filter);
		}
	};
	
	/**
	 * Worker that will monitor media streaming states.
	 */
	protected Thread stateMonitor = new Thread("stateMonitor$Worker") {
		
		private boolean mMusicPlaying = false;
		private boolean mInKeyguard = false;
		
		public void run() {
			Utils.log(Level.INFO, TAG, "Media listener worker is started");
			
			boolean newStreamApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
			ReflectMethod keyguardMethod = mWindowManagerPolicy.findMethod("inKeyguardRestrictedKeyInputMode");
			ReflectMethod streamCheckMethod = null;
			
			if (newStreamApi) {
				streamCheckMethod = mAudioSystem.findMethod("isStreamActive", Match.BEST, Integer.TYPE, Integer.TYPE);
				
			} else {
				streamCheckMethod = mAudioSystem.findMethod("isStreamActive", Match.BEST, Integer.TYPE);
			}
			
			while (true) {
				try {
					boolean isMusicPlaying = false;
					
					if (newStreamApi) {
						isMusicPlaying = (Boolean) streamCheckMethod.invoke(STREAM_MUSIC, 0);
						
					} else {
						isMusicPlaying = (Boolean) streamCheckMethod.invoke(STREAM_MUSIC);
					}
					
					if (isMusicPlaying != mMusicPlaying) {
						mMusicPlaying = isMusicPlaying;
						
						Utils.log(Level.DEBUG, TAG, "Alert about media change [music=" + (isMusicPlaying ? "on" : "off") + "]");

						mBackendMgr.sendListenerMsg(Constants.BRC_SL_MEDIA, new HashBundle("music_playing", isMusicPlaying));
					}
					
					boolean inKeyguard = (Boolean) keyguardMethod.invoke();
					
					if (inKeyguard != mInKeyguard) {
						mInKeyguard = inKeyguard;
						
						Utils.log(Level.DEBUG, TAG, "Alert about keyguard state change [keyguard=" + (inKeyguard ? "on" : "off") + "]");

                        mBackendMgr.sendListenerMsg(Constants.BRC_SL_KEYGUARD, new HashBundle("keyguard_on", inKeyguard));
					}

					synchronized (this) {
						wait(1000);
					}
					
				} catch (InterruptedException e) {}
			}
		}
	};
	
	/**
	 * Listener to obtain any change the telephony state
	 */
	protected PhoneStateListener telephonyListener = new PhoneStateListener() {
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			Utils.log(Level.DEBUG, TAG, "Alert about telephony change with state id " + state);
			
			HashBundle data = new HashBundle();
			data.putInt("state", state);
			data.putString("number", incomingNumber);

            mBackendMgr.sendListenerMsg(Constants.BRC_SL_TELEPHONY, data);
		}
	};
	
	/**
	 * This listener will report when a user is switched by another.
	 * It can be used to implement proper multi-user support by supplying 
	 * the type of information that Google should have had added themselves.
	 * Or rather should not have made hidden or system protected. 
	 */
	protected MethodBridge userSwitchListener = new MethodBridge() {
        @Override
        public void bridgeBegin(BridgeParams param) {
			Utils.log(Level.DEBUG, TAG, "Alert about an upcoming user switch from userid " + param.args[1] + " to userid " + param.args[2]);

            HashBundle data = new HashBundle();
			data.putBoolean("switched", false);
			data.putInt("current_userid", (Integer) param.args[1]);
			data.putInt("affected_userid", (Integer) param.args[2]);
			
			/*
			 * Send broadcast alerting about an upcoming user switch
			 */
            mBackendMgr.sendListenerMsg(Constants.BRC_SL_USERSWITCH, data);
		}
		
		@Override
        public void bridgeEnd(BridgeParams param) {
			Utils.log(Level.DEBUG, TAG, "Alert that the current user has changed from " + param.args[1] + " to userid " + param.args[2]);

            HashBundle data = new HashBundle();
			data.putBoolean("switched", true);
			data.putInt("current_userid", (Integer) param.args[2]);
			data.putInt("affected_userid", (Integer) param.args[1]);
			
			/*
			 * Send broadcast alerting that the current user has been changed
			 */
            mBackendMgr.sendListenerMsg(Constants.BRC_SL_USERSWITCH, data);
		}
	};
	
	/**
	 * This listener will report any change in activity focus. 
	 * It can be used to keep track of the current used application.
	 */
	protected MethodBridge applicationFocusedListener = new MethodBridge() {
		
		private boolean mSendBroadcast = false;
		
		@Override
        public void bridgeBegin(BridgeParams param) {
			Utils.log(Level.DEBUG, TAG, "Checking new activity focus change");

			Object focusedActivity = mActivityManagerService.findField("mFocusedActivity").getValue();
			
			/*
			 * The original method does not return any indication whether or not something has changed. 
			 * So we need to check this ourself.
			 */
			if (focusedActivity == null || !focusedActivity.equals( param.args[0] )) {
				mSendBroadcast = true;
				
			} else {
				mSendBroadcast = false;
			}
		}
		
		@Override
        public void bridgeEnd(BridgeParams param) {
			if (mSendBroadcast) {
				ReflectClass activityRecord = ReflectClass.fromReceiver(param.args[0]);
				ActivityInfo activityInfo = (ActivityInfo) activityRecord.findField("info").getValue();
				
				Utils.log(Level.DEBUG, TAG, "Alert about activity change in package " + activityInfo.packageName);

                mBackendMgr.sendListenerMsg(Constants.BRC_SL_APPFOCUS, new HashBundle("activity_info", activityInfo));
			}
		}
	};

    /**
     * This keeps track of the Soft Keyboard visibillity
     */
    protected MethodBridge softKeyboardListener = new MethodBridge() {

        boolean mIsShowing = false;

        @Override
        public void bridgeEnd(BridgeParams param) {
            boolean isShowing = (Boolean) param.getResult() && param.method.getName().startsWith("show");

            if (mIsShowing != isShowing) {
                mIsShowing = isShowing;

                Utils.log(Level.DEBUG, TAG, "Alert about Soft Keyboard visibillity change");

                mBackendMgr.sendListenerMsg(Constants.BRC_SL_SOFTKEYBOARD, new HashBundle("isShowing", mIsShowing));
            }
        }
    };
	
	/**
	 * This receiver is used for everything that can be handled without hooks
	 */
	protected BroadcastReceiver stateBroadcastReveiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF) || intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				boolean screenOn = intent.getAction().equals(Intent.ACTION_SCREEN_ON);
				
				Utils.log(Level.DEBUG, TAG, "Alert about screen state change [display=" + (screenOn ? "on" : "off") + "]");

                mBackendMgr.sendListenerMsg(Constants.BRC_SL_DISPLAY, new HashBundle("screen_on", screenOn));
			}
		}
	};
}
