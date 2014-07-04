package com.spazedog.xposed.additionsgb.backend.pwm;

import java.util.ArrayList;
import java.util.List;

import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Settings;

import android.os.SystemClock;
import android.view.ViewConfiguration;

public class EventManager {
	public static enum State { PENDING, ONGOING, INVOKED, INVOKED_DEFAULT, CANCELED }
	public static enum Priority { PRIMARY, SECONDARY }
	
	private XServiceManager mXServiceManager;
	
	private List<Integer> mOnGoingKeyCodes = new ArrayList<Integer>();
	
	private Integer mTapTimeout = 0;
	private Integer mPressTimeout = 0;
	
	private String[] mClickActions = new String[3];
	private String[] mPressActions = new String[3];
	
	private Boolean mIsCallButton = false;
	private Boolean mIsExtended = false;
	
	private State mState = State.PENDING;
	private Boolean mIsDownEvent = false;
	private Boolean mIsCombiEvent = false;
	private Integer mTapCount = 0;
	private Long mEventTime = 0L;
	private Long mDownTime = 0L;
	
	private Boolean mIsScreenOn = true;
	private String mCurrentApplication;
	
	private EventKey mPrimaryKey = new EventKey(Priority.PRIMARY);
	private EventKey mSecondaryKey = new EventKey(Priority.SECONDARY);
	
	private final Object mLock = new Object();
	
	public EventManager(XServiceManager xserviceManager) {
		mXServiceManager = xserviceManager;
	}
	
	public void registerEvent(String currentApplication, Boolean inKeyguard, Boolean isScreenOn) {
		synchronized (mLock) {
			mIsExtended = mXServiceManager.isPackageUnlocked();
			mIsCallButton = mXServiceManager.getBooleanGroup(Settings.REMAP_KEY_ENABLE_CALLBTN, (mPrimaryKey.mKeyCode + ":" + mSecondaryKey.mKeyCode));
			mTapTimeout = mXServiceManager.getInt(Settings.REMAP_TIMEOUT_DOUBLECLICK, ViewConfiguration.getDoubleTapTimeout());
			mPressTimeout = mXServiceManager.getInt(Settings.REMAP_TIMEOUT_LONGPRESS, ViewConfiguration.getLongPressTimeout());
			mCurrentApplication = inKeyguard ? "keyguard" : currentApplication;
			mIsScreenOn = isScreenOn;
			
			/*
			 * This array is to help extract the actions from the preference array.
			 * Since triple actions was not added until later, these was placed at the end to 
			 * keep compatibility with already existing preference files. 
			 * 
			 *  - 0 = Click
			 *  - 1 = Double Click
			 *  - 2 = Long Press
			 *  - 3 = Double Long Press
			 *  - 4 = Triple Click
			 *  - 5 = Triple Long Press
			 */
			Integer[] oldConfig = new Integer[]{0,1,4,2,3,5};
			String keyGroupName = mPrimaryKey.mKeyCode + ":" + mSecondaryKey.mKeyCode;
			String appCondition = !isScreenOn ? null : inKeyguard ? "guard" : mIsExtended ? currentApplication : null;
			List<String> actions = appCondition != null ? mXServiceManager.getStringArrayGroup(Settings.REMAP_KEY_LIST_ACTIONS.get(appCondition), keyGroupName, null) : null;
			
			if ((mIsCombiEvent && !mIsExtended) || (actions == null && (actions = mXServiceManager.getStringArrayGroup(Settings.REMAP_KEY_LIST_ACTIONS.get(isScreenOn ? "on" : "off"), keyGroupName, null)) == null)) {
				actions = new ArrayList<String>();
			}
			
			for (int i=0; i < oldConfig.length; i++) {
				Integer x = oldConfig[i];
				
				/*
				 * Only include Click and Long Press along with excluding Application Launch on non-pro versions
				 */
				String action = ((x == 0 || x == 2) || mIsExtended) && actions.size() > x && (mIsExtended || !".".equals(actions.get(x))) ? actions.get(x) : null;
				
				if (i < 3) {
					mClickActions[i] = action;
					
				} else {
					mPressActions[i-3] = action;
				}
			}
		}
	}
	
	public Boolean registerKey(Integer keyCode, Boolean isKeyDown, Integer policyFlags) {
		synchronized (mLock) {
			Long time = SystemClock.uptimeMillis();
			Boolean newEvent = false;
			
			if (isKeyDown) {
				if ((time - mEventTime) > 1000) {
					mState = State.PENDING;
				}
				
				if (mState == State.ONGOING && (keyCode == mPrimaryKey.mKeyCode || keyCode == mSecondaryKey.mKeyCode)) {
					mTapCount += 1;
					
					if (keyCode == mSecondaryKey.mKeyCode) {
						mSecondaryKey.mIsKeyDown = true;
						
					} else {
						mPrimaryKey.mIsKeyDown = true;
					}
					
				} else if (mState != State.CANCELED && mState != State.PENDING && mPrimaryKey.isKeyDown() && keyCode != mPrimaryKey.mKeyCode && (mSecondaryKey.mKeyCode == 0 || keyCode == mSecondaryKey.mKeyCode)) {
					mState = State.ONGOING;
					mTapCount = 0;
					mIsCombiEvent = true;
					
					mSecondaryKey.mKeyCode = keyCode;
					mSecondaryKey.mPolicyFlags = policyFlags;
					mSecondaryKey.mIsKeyDown = true;
					
					newEvent = true;
					
				} else {
					mState = State.ONGOING != mState ? State.ONGOING : State.CANCELED;
					
					if (State.ONGOING == mState) {
						mTapCount = 0;
						mDownTime = time;
						mIsCombiEvent = false;
						
						mPrimaryKey.mKeyCode = keyCode;
						mPrimaryKey.mPolicyFlags = policyFlags;
						mPrimaryKey.mIsKeyDown = true;
						
						mSecondaryKey.mKeyCode = 0;
						mSecondaryKey.mPolicyFlags = 0;
						mSecondaryKey.mIsKeyDown = false;
						
						newEvent = true;
						
					} else {
						return false;
					}
				}
				
				mIsDownEvent = true;
				mIsCallButton = false;
				
				mPrimaryKey.mRepeatCount = 0;
				mSecondaryKey.mRepeatCount = 0;
				
			} else {
				if (keyCode == mSecondaryKey.mKeyCode) {
					mSecondaryKey.mIsKeyDown = false;
					
				} else {
					mPrimaryKey.mIsKeyDown = false;
				}
				
				mIsDownEvent = false;
			}
			
			mEventTime = time;
			
			mPrimaryKey.mIsLastQueued = mPrimaryKey.mKeyCode == keyCode;
			mSecondaryKey.mIsLastQueued = mSecondaryKey.mKeyCode == keyCode;
			
			return newEvent;
		} 
	}
	
	public EventKey getEventKey(Integer keyCode) {
		return mPrimaryKey.getKeyCode() == keyCode ? mPrimaryKey : 
				mSecondaryKey.getKeyCode() == keyCode ? mSecondaryKey : null;
	}
	
	public EventKey getEventKey(Priority priority) {
		switch (priority) {
			case PRIMARY: return mPrimaryKey;
			case SECONDARY: return mSecondaryKey;
		}
		
		return null;
	}
	
	public EventKey getParentEventKey(Integer keyCode) {
		return mPrimaryKey.getKeyCode() == keyCode ? mSecondaryKey : 
				mSecondaryKey.getKeyCode() == keyCode ? mPrimaryKey : null;
	}
	
	public EventKey getParentEventKey(Priority priority) {
		switch (priority) {
			case PRIMARY: return mSecondaryKey;
			case SECONDARY: return mPrimaryKey;
		}
		
		return null;
	}
	
	public Boolean isCombiEvent() {
		return mIsCombiEvent;
	}
	
	public Boolean isDownEvent() {
		return mIsDownEvent;
	}
	
	public Boolean isCallButtonEvent() {
		return mIsCallButton;
	}
	
	public Boolean isScreenOn() {
		return mIsScreenOn;
	}
	
	public Boolean hasExtendedFeatures() {
		return mIsExtended;
	}
	
	public Boolean hasTapActions() {
		return mClickActions[1] != null ||
				mClickActions[2] != null || 
				mPressActions[1] != null ||
				mPressActions[2] != null;
	}
	
	public Boolean hasOngoingKeyCodes() {
		return mOnGoingKeyCodes.size() > 0;
	}
	
	public Boolean hasOngoingKeyCodes(Integer keyCode) {
		return mOnGoingKeyCodes.contains((Object) keyCode);
	}
	
	public Integer[] clearOngoingKeyCodes(Boolean returList) {
		Integer[] keys = null; 
		
		if (returList) {
			keys = mOnGoingKeyCodes.toArray(new Integer[mOnGoingKeyCodes.size()]);
		}
		
		mOnGoingKeyCodes.clear();
		
		return keys;
	}
	
	public void addOngoingKeyCode(Integer keyCode) {
		if (!mOnGoingKeyCodes.contains((Object) keyCode)) {
			mOnGoingKeyCodes.add(keyCode);
		}
	}
	
	public void removeOngoingKeyCode(Integer keyCode) {
		mOnGoingKeyCodes.remove((Object) keyCode);
	}
	
	public String getAction(Boolean isKeyDown) {
		return getAction(isKeyDown, mTapCount);
	}
	
	public String getAction(Boolean isKeyDown, Integer tapCount) {
		return isKeyDown ? 
				(tapCount < mPressActions.length ? mPressActions[tapCount] : null) : 
					(tapCount < mClickActions.length ? mClickActions[tapCount] : null);
	}
	
	public Integer getTapTimeout() {
		return mTapTimeout;
	}
	
	public Integer getPressTimeout() {
		return mPressTimeout;
	}
	
	public Long getDownTime() {
		return mDownTime;
	}
	
	public Long getEventTime() {
		return mEventTime;
	}
	
	public State getState() {
		return mState;
	}
	
	public Integer getTapCount() {
		return mTapCount;
	}
	
	public String getCurrentApplication() {
		return mCurrentApplication;
	}
	
	public void cancelEvent() {
		cancelEvent(false);
	}
	
	public void cancelEvent(Boolean forcedReset) {
		synchronized (mLock) {
			if (mState == State.ONGOING || forcedReset) {
				mState = forcedReset ? State.PENDING : State.CANCELED;
			}
		}
	}
	
	public void invokeEvent() {
		synchronized (mLock) {
			if (mState == State.ONGOING) {
				mState = State.INVOKED;
			}
		}
	}
	
	public void invokeDefaultEvent(Integer keyCode) {
		synchronized (mLock) {
			if (mState == State.ONGOING || mState == State.INVOKED_DEFAULT) {
				EventKey key = getEventKey(keyCode);
				
				if (key != null) {
					mState = State.INVOKED_DEFAULT;
					key.mRepeatCount += 1;
					
				} else {
					mState = State.CANCELED;
				}
			}
		}
	}
}
