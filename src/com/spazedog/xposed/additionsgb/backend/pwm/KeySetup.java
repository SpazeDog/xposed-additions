package com.spazedog.xposed.additionsgb.backend.pwm;

import java.util.ArrayList;
import java.util.List;

import android.view.ViewConfiguration;

import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.configs.Settings;

public class KeySetup {
	private XServiceManager mXServiceManager;
	
	private Boolean mHasExtended = false;
	
	private Integer mTapTimeout = 0;
	private Integer mPressTimeout = 0;
	
	private String[] mActions = new String[6];
	
	private Boolean[] mHasClick = new Boolean[] {false, false, false};
	private Boolean[] mHasPress = new Boolean[] {false, false, false};
	private Boolean mHasSingleClick = false;
	private Boolean mHasSinglePress = false;
	private Boolean mHasTapClick = false;
	private Boolean mHasTapPress = false;
	
	public KeySetup(XServiceManager xserviceManager) {
		mXServiceManager = xserviceManager;
	}
	
	public void registerEvent(Integer primaryKey, Integer secondaryKey, Boolean isCombo, String runningPackage, Boolean inKeyguard, Boolean isScreenOn) {
		mHasExtended = mXServiceManager.isPackageUnlocked();
		mTapTimeout = mXServiceManager.getInt(Settings.REMAP_TIMEOUT_DOUBLECLICK, ViewConfiguration.getDoubleTapTimeout());
		mPressTimeout = mXServiceManager.getInt(Settings.REMAP_TIMEOUT_LONGPRESS, ViewConfiguration.getLongPressTimeout());
		
		/*
		 *  TODO: Clean this method up a bit.
		 *  
		 * Change settings array {click, double click, press, double press, triple click, triple press}
		 * Into temp array {click, double click, triple click, press, double press, triple press}
		 * 
		 * This is to support older configs that only contained 3 actions. 
		 */
		Integer[] pos = new Integer[]{0,1,3,4,2,5};
		String keyGroupName = primaryKey + ":" + secondaryKey;
		String appCondition = !isScreenOn ? null : inKeyguard ? "guard" : mHasExtended ? runningPackage : null;
		List<String> actions = appCondition != null ? mXServiceManager.getStringArrayGroup(Settings.REMAP_KEY_LIST_ACTIONS.get(appCondition), keyGroupName, null) : null;
		
		if ((isCombo && !mHasExtended) ||
				(actions == null && (actions = mXServiceManager.getStringArrayGroup(Settings.REMAP_KEY_LIST_ACTIONS.get(isScreenOn ? "on" : "off"), keyGroupName, null)) == null)) {
			
			actions = new ArrayList<String>();
		}
		
		/*
		 * This should never be wrapped in any condition. No mater how the 'actions' list look, the values below needs to be reset. 
		 */
		for (int i=0; i < pos.length; i++) {
			mActions[ pos[i] ] = ((pos[i] != 1 && pos[i] != 2 && pos[i] != 4 && pos[i] != 5) || mHasExtended) && 
					actions.size() > i && actions.get(i) != null && (mHasExtended || !actions.get(i).contains(".")) ? actions.get(i) : null;
			
			switch (pos[i]) {
				case 0: mHasSingleClick = mHasClick[0] = mActions[ pos[i] ] != null; break;
			
				case 1:
				case 2: mHasClick[pos[i]] = mActions[ pos[i] ] != null; 
				
						if (!mHasTapClick) {
							mHasTapClick = mHasClick[pos[i]];
						}
						break;
					
				case 3: mHasSinglePress = mHasPress[0] = mActions[ pos[i] ] != null; break;
				
				case 4:
				case 5: mHasPress[pos[i]-3] = mActions[ pos[i] ] != null;
				
						if (!mHasTapPress) {
							mHasTapPress = mHasPress[pos[i]-3];
						}
			}
		}
	}
	
	public Boolean hasClicks() {
		for (int i=0; i < mHasClick.length; i++) {
			if (mHasClick[i]) {
				return true;
			}
		}
		
		return false;
	}
	
	public Boolean hasPresses() {
		for (int i=0; i < mHasPress.length; i++) {
			if (mHasPress[i]) {
				return true;
			}
		}
		
		return false;
	}
	
	public Boolean hasClick(Integer tapCount) {
		return tapCount >= mHasClick.length ? false : mHasClick[tapCount];
	}
	
	public Boolean hasPress(Integer tapCount) {
		return tapCount >= mHasPress.length ? false : mHasPress[tapCount];
	}
	
	public Boolean hasSingleClick() {
		return mHasSingleClick;
	}
	
	public Boolean hasTapClick() {
		return mHasTapClick;
	}
	
	public Boolean hasSinglePress() {
		return mHasSinglePress;
	}
	
	public Boolean hasTapPress() {
		return mHasTapPress;
	}
	
	public Boolean hasExtendedFeatures() {
		return mHasExtended;
	}
	
	public Integer getTapTimeout() {
		return mTapTimeout;
	}
	
	public Integer getPressTimeout() {
		return mPressTimeout;
	}
	
	public String getClickAction(Integer tapCount) {
		return tapCount < mActions.length ? mActions[tapCount] : mActions[mActions.length-1];
	}
	
	public String getPressAction(Integer tapCount) {
		return (tapCount+=3) < mActions.length ? mActions[tapCount] : mActions[mActions.length-1];
	}
}
