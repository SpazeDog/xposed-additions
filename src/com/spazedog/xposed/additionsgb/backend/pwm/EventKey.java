package com.spazedog.xposed.additionsgb.backend.pwm;

import com.spazedog.xposed.additionsgb.backend.pwm.EventManager.Priority;

public class EventKey {
	
	private final Priority mPriority;
	protected Integer mKeyCode = 0;
	protected Integer mPolicyFlags = 0;
	protected Boolean mIsKeyDown = false;
	protected Boolean mIsLastQueued = false;
	protected Integer mRepeatCount = 0;
	
	public EventKey(Priority priority) {
		mPriority = priority;
	}
	
	public Priority getPriority() {
		return mPriority;
	}
	
	public Integer getKeyCode() {
		return mKeyCode;
	}
	
	public Integer getPolicFlags() {
		return mPolicyFlags;
	}
	
	public Boolean isKeyDown() {
		return mIsKeyDown;
	}
	
	public Boolean isLastQueued() {
		return mIsLastQueued;
	}
	
	public Integer getRepeatCount() {
		return mRepeatCount;
	}
}
