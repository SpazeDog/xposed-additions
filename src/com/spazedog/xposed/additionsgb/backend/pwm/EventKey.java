package com.spazedog.xposed.additionsgb.backend.pwm;

import android.view.KeyEvent;

public class EventKey {
	
	private Long mDownTime;
	private Integer mKeyCode;
	private Integer mFlags;
	private Integer mRepeatCount;
	private Boolean mIsPressed;
	private Boolean mIsOnGoing;
	
	private EventManager mManager;
	
	protected EventKey(EventManager manager) {
		mManager = manager;
	}
	
	protected void initiateInstance(Integer keyCode, Integer flags, Long downTime) {
		mIsOnGoing = false;
		mRepeatCount = 0;
		mKeyCode = keyCode;
		mFlags = flags;
		mDownTime = downTime;
	}
	
	protected void updateInstance(Boolean pressed) {
		mIsPressed = pressed;
	}
	
	public Long getDownTime() {
		return mDownTime;
	}

	public Integer getPosition() {
		return mManager.getKeyCodePosition(mKeyCode);
	}

	public Integer getCode() {
		return mKeyCode;
	}

	public Integer getFlags() {
		return mFlags;
	}

	public Integer getRepeatCount() {
		return mRepeatCount;
	}

	public Boolean isPressed() {
		return mIsPressed;
	}

	public Boolean isLastQueued() {
		return mManager.getLastQueuedKeyCode().equals(mKeyCode);
	}

	public Boolean isOnGoing() {
		return mIsOnGoing;
	}
	
	public void invokeAndRelease() {
		if (!mIsOnGoing) {
			for (int i=0; i < mManager.getKeyCount(); i++) {
				EventKey combo = mManager.getKeyAt(i);
				
				if (combo != null && !combo.mKeyCode.equals(mKeyCode) && !combo.mIsOnGoing) {
					combo.mIsOnGoing = true;
					mManager.injectInputEvent(combo.mKeyCode, KeyEvent.ACTION_DOWN, 0L, 0L, 0, combo.mFlags);
				}
			}
			
			mManager.injectInputEvent(mKeyCode, KeyEvent.ACTION_MULTIPLE, 0L, 0L, 0, mFlags);
			
		} else {
			release();
		}
	}
	
	public void invoke() {
		if (mIsPressed) {
			if (!mIsOnGoing) {
				for (int i=0; i < mManager.getKeyCount(); i++) {
					EventKey combo = mManager.getKeyAt(i);
					
					if (combo != null && !combo.mKeyCode.equals(mKeyCode) && !combo.mIsOnGoing) {
						combo.mIsOnGoing = true;
						mManager.injectInputEvent(combo.mKeyCode, KeyEvent.ACTION_DOWN, 0L, 0L, 0, combo.mFlags);
					}
				}

				mIsOnGoing = true;
			}

			mRepeatCount += 1;
			mManager.injectInputEvent(mKeyCode, KeyEvent.ACTION_DOWN, 0L, 0L, mRepeatCount-1, mFlags);
		}
	}
	
	public void release() {
		if (mIsOnGoing) {
			Boolean wasRepeat = mRepeatCount > 1;
			
			mRepeatCount = 0;
			mIsOnGoing = false;
			mManager.injectInputEvent(mKeyCode, KeyEvent.ACTION_UP, 0L, 0L, wasRepeat ? 1 : 0, getFlags());
		}
	}
}
