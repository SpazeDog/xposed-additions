package com.spazedog.xposed.additionsgb.backend.pwm;

import android.os.SystemClock;

import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Settings;

public class KeyFlags {
	public static enum State { PENDING, ONGOING, INVOKE, CANCEL, RESET, DEFAULT }
	
	private XServiceManager mXServiceManager;
	
	private State mState = State.PENDING;
	private Integer mTapCount = 0;
	private Boolean mIsCallButton = false;
	private Boolean mIsPrimaryDown = false;
	private Boolean mIsSecondaryDown = false;
	private Integer mPrimaryFlags = 0;
	private Integer mSecondaryFlags = 0;
	
	private Integer mPrimaryKey = 0;
	private Integer mSecondaryKey = 0;
	private Integer mCurrentKey = 0;
	
	private Long mEventTime = 0L;
	private Long mDownTime = 0L;
	
	public KeyFlags(XServiceManager xserviceManager) {
		mXServiceManager = xserviceManager;
	}
	
	public Boolean registerKey(Integer keyCode, Boolean keyDown, Integer flags) {
		synchronized (mState) {
			Long time = SystemClock.uptimeMillis();
			Boolean newEvent = true;
			
			mCurrentKey = keyCode;
			
			if (keyDown) {
				if ((time - mEventTime) > 1000) {
					mState = State.RESET;
				}
				
				/*
				 * Repeated click. This means that we can trigger double click, triple click etc. 
				 * If the user clicks the same button multiple times without the state changes, another repeat count is added. 
				 * This can also be a combo repeated click if the user clicks the same secondary button while keeping the primary pressed. 
				 */
				if (mState == State.ONGOING && (keyCode == mPrimaryKey || keyCode == mSecondaryKey)) {
					newEvent = false;
					mTapCount += 1;
					
					if (keyCode == mSecondaryKey) {
						mIsSecondaryDown = true;
						
					} else {
						mIsPrimaryDown = true;
					}
					
				/*
				 * Combo click. If the user presses and holds one button while pressing another, a combo is added with the first as primary and the second as secondary. 
				 * If the user releases the secondary and re-presses it, without releasing the primary, a new combo is created with the same keys. This however only 
				 * applies if the state changed to PENDING in between. If it was not changed from ONGOING, we will get a repeat in the above condition instead. If it was changed to CANCEL or RESET, 
				 * a new event is created in the below condition. 
				 */
				} else if (mState != State.CANCEL && mState != State.RESET && mIsPrimaryDown && keyCode != mPrimaryKey && (mSecondaryKey == 0 || mSecondaryKey == keyCode)) {
					mIsSecondaryDown = true;
					mSecondaryKey = keyCode;
					mSecondaryFlags = flags;
					mState = State.ONGOING;
					mTapCount = 0;
					
				/*
				 * If neither of the above conditions matched, we will create a new event and reset all current properties. 
				 */
				} else {
					mState = State.ONGOING;
					mTapCount = 0;
					mIsPrimaryDown = true;
					mIsSecondaryDown = false;
					mPrimaryKey = keyCode;
					mSecondaryKey = 0;
					mPrimaryFlags = flags;
					mSecondaryFlags = 0;
					mDownTime = time;

					mIsCallButton = mXServiceManager.getBooleanGroup(Settings.REMAP_KEY_ENABLE_CALLBTN, (mPrimaryKey + ":" + mSecondaryKey));
				}
				
			} else {
				newEvent = false;
				
				if (keyCode == mPrimaryKey) {
					mIsPrimaryDown = false;
					
				} else if (keyCode == mSecondaryKey) {
					mIsSecondaryDown = false;
				}
			}
			
			mEventTime = time;
			
			return newEvent;
		}
	}
	
	public void refresh() {
		mState = State.PENDING;
		mTapCount = 0;
		mIsCallButton = false;
		mIsPrimaryDown = false;
		mIsSecondaryDown = false;
		mPrimaryFlags = 0;
		mSecondaryFlags = 0;
		
		mPrimaryKey = 0;
		mSecondaryKey = 0;
		mCurrentKey = 0;
		
		mEventTime = 0L;
		mDownTime = 0L;
	}
	
	public void invoke() {
		invoke(false);
	}
	
	public void invoke(Boolean original) {
		synchronized (mState) {
			if (mState == State.ONGOING) {
				mState = original ? State.DEFAULT : State.INVOKE;
			}
		}
	}
	
	public void cancel() {
		cancel(false);
	}
	
	public void cancel(Boolean reset) {
		synchronized (mState) {
			if (reset) {
				mState = State.RESET;
				
			} else if (mState == State.ONGOING) {
				mState = State.CANCEL;
			}
		}
	}
	
	public Boolean hasState(State... states) {
		for (int i=0; i < states.length; i++) {
			if (states[i] == mState) {
				return true;
			}
		}
		
		return false;
	}
	
	public Boolean isKeyDown() {
		return mPrimaryKey > 0 && mIsPrimaryDown && (mSecondaryKey == 0 || mIsSecondaryDown);
	}
	
	public Boolean isComboAction() {
		return mPrimaryKey > 0 && mSecondaryKey > 0;
	}
	
	public Boolean isCallButton() {
		return mIsCallButton;
	}
	
	public Integer getTapCount() {
		return mTapCount;
	}
	
	public Integer getPrimaryFlags() {
		return mPrimaryFlags;
	}
	
	public Integer getSecondaryFlags() {
		return mSecondaryFlags;
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
	
	public Integer getKeyLevel(Integer keyCode) {
		return keyCode == mPrimaryKey ? 1 : 
			keyCode == mSecondaryKey ? 2 : 0;
	}
	
	public Long getDownTime() {
		return mDownTime;
	}
	
	public Long getEventTime() {
		return mEventTime;
	}
}
