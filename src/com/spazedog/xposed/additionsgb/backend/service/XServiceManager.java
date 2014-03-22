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

package com.spazedog.xposed.additionsgb.backend.service;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.util.Log;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.xposed.additionsgb.Common;

/*
 * This manager makes it easier to work with the XService. 
 * On top of that, the manager allows you to register a Context on which a BroadcastReceiver
 * will be added along with a data cache to make it faster acquiring key values. The
 * Receiver will make sure to keep the cache updated and avoid having to request values via IPC each time
 * you call the get methods. 
 */
public class XServiceManager {
	public static final String TAG = XServiceManager.class.getName();
	
	private static WeakReference<XServiceManager> oInstance = new WeakReference<XServiceManager>(null);
	
	private IXService mService;
	
	private Context mContext;
	
	private Boolean mUseCache = false;
	
	private Boolean mIsUnlocked;
	
	private Boolean isReady;
	
	private Map<String, Object> mData = new HashMap<String, Object>();
	
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			synchronized (mData) {
				if (oInstance.get() != null) {
					try {
						if ("preference_change".equals(intent.getStringExtra("action"))) {
							String key = intent.getStringExtra("key");
							final Integer type = mService.getType(key);
							
							switch(type) {
								case XService.TYPE_STRING: mData.put(key, mService.getString(key, "")); break;
								case XService.TYPE_INTEGER: mData.put(key, mService.getInt(key, -1)); break;
								case XService.TYPE_BOOLEAN: mData.put(key, mService.getBoolean(key, false)); break;
								case XService.TYPE_LIST: mData.put(key, mService.getStringArray(key, new ArrayList<String>())); break;
								case XService.TYPE_EMPTY: mData.remove(key);
							}
							
						} else {
							mIsUnlocked = mService.isUnlocked();
						}
					
					} catch (RemoteException e) { handleRemoteException(e); }
					
				} else {
					if(Common.debug()) Log.d(TAG, "Removing unused BroadcastReceiver for '" + context.getPackageName() + "'");
					
					context.unregisterReceiver(this);
				}
			}
		}
	};
	
	public static synchronized XServiceManager getInstance() {
		XServiceManager instance = oInstance.get();
		
		if (instance == null || instance.mService == null) {
			if (instance == null) {
				instance = new XServiceManager();
			}
			
			instance.mService = (IXService) new ReflectClass(IXService.class).bindInterface(Common.XSERVICE_NAME).getReceiver();
			
			if (instance.mService != null) {
				oInstance = new WeakReference<XServiceManager>(instance);
				
			} else {
				instance = null;
			}
		}
		
		return instance;
	}
	
	private synchronized void handleRemoteException(RemoteException e) {
		mService = (IXService) new ReflectClass(IXService.class).bindInterface(Common.XSERVICE_NAME).getReceiver();
	}
	
	private XServiceManager(){}
	
	public void registerContext(Context context) {
		if(Common.debug()) Log.d(TAG, "Adding Context and BroadcastReceiver to '" + context.getPackageName() + "' instance");
		
		/*
		 * The outer most System Context returns NULL on getApplicationContext()
		 */
		mContext = context.getApplicationContext() == null ? context : context.getApplicationContext();
		mContext.registerReceiver(
				mBroadcastReceiver, 
				new IntentFilter(Common.XSERVICE_BROADCAST));
		
		mUseCache = true;
	}
	
	public void unregisterContext() {
		if (mContext != null) {
			mContext.unregisterReceiver(mBroadcastReceiver);
			mContext = null;
			
			mUseCache = false;
			
			/*
			 * The cache will no longer be updated when there is no context registered. 
			 * In case it get's re-registered, we don't want an outdated cache laying around. 
			 */
			mData = new HashMap<String, Object>();
		}
	}
	
	public Integer getIntGroup(String group, String key) {
		return getInt(group + "#" + key, -1);
	}
	
	public Integer getIntGroup(String group, String key, Integer defaultValue) {
		return getInt(group + "#" + key, defaultValue);
	}
	
	public Boolean getBooleanGroup(String group, String key) {
		return getBoolean(group + "#" + key, false);
	}
	
	public Boolean getBooleanGroup(String group, String key, Boolean defaultValue) {
		return getBoolean(group + "#" + key, defaultValue);
	}
	
	public String getStringGroup(String group, String key) {
		return getString(group + "#" + key, null);
	}
	
	public String getStringGroup(String group, String key, String defaultValue) {
		return getString(group + "#" + key, defaultValue);
	}

	public List<String> getStringArrayGroup(String group, String key) {
		return getStringArray(group + "#" + key, null);
	}
	
	public List<String> getStringArrayGroup(String group, String key, ArrayList<String> defaultValue) {
		return getStringArray(group + "#" + key, defaultValue);
	}

	public void putIntGroup(String group, String key, Integer value, Boolean preserve) {
		putInt(group + "#" + key, value, preserve);
	}

	public void putIntGroup(String group, String key, Integer value) {
		putInt(group + "#" + key, value);
	}
	
	public void putBooleanGroup(String group, String key, Boolean value, Boolean preserve) {
		putBoolean(group + "#" + key, value, preserve);
	}
	
	public void putBooleanGroup(String group, String key, Boolean value) {
		putBoolean(group + "#" + key, value);
	}
	
	public void putStringGroup(String group, String key, String value, Boolean preserve) {
		putString(group + "#" + key, value, preserve);
	}
	
	public void putStringGroup(String group, String key, String value) {
		putString(group + "#" + key, value);
	}
	
	public void putStringArrayGroup(String group, String key, ArrayList<String> value, Boolean preserve) {
		putStringArray(group + "#" + key, value, preserve);
	}
	
	public void putStringArrayGroup(String group, String key, ArrayList<String> value) {
		putStringArray(group + "#" + key, value);
	}
	
	public boolean removeGroup(String group, String key) {
		try {
			List<String> keys = mService.getKeys();
			Boolean status = true;
			
			for (String arrKey : keys) {
				if ((group != null && key != null && arrKey.equals(group + "#" + key)) ||
						(key == null && arrKey.startsWith(group + "#")) ||
						group == null && arrKey.endsWith("#" + key)) {
					
					try {
						if(Common.debug()) Log.d(TAG, "Removing group array '" + arrKey + "'");
						
						mService.remove(arrKey);
						
					} catch (RemoteException e) { 
						if(Common.debug()) Log.d(TAG, "The group array '" + arrKey + "' could not be removed");
						
						handleRemoteException(e); 
						status = false; 
					}
				}
			}
			
			return status;
			
		} catch (RemoteException e) { 
			if(Common.debug()) Log.d(TAG, "It was not possible to get all group keys in order to remove " + (group == null ? "" : group) + "#" + (key == null ? "" : key));
			
			handleRemoteException(e); 
		}
		
		return false;
	}

	public Integer getInt(String key) {
		return getInt(key, -1);
	}
	
	public Integer getInt(String key, Integer defaultValue) {
		try {
			if (!mUseCache) {
				if(Common.debug()) Log.d(TAG, "Retrieving preference Integer '" + key + "' via IPC");
				
				return mService.getInt(key, defaultValue);
				
			} else if (!mData.containsKey(key)) {
				mData.put(key, mService.getInt(key, defaultValue));
			}
			
			if(Common.debug()) Log.d(TAG, "Retrieving preference Integer '" + key + "' from Cache");
			
			return (Integer) mData.get(key);
			
		} catch (RemoteException e) { handleRemoteException(e); return defaultValue; }
	}

	public Boolean getBoolean(String key) {
		return getBoolean(key, false);
	}
	
	public Boolean getBoolean(String key, Boolean defaultValue) {
		try {
			if (!mUseCache) {
				if(Common.debug()) Log.d(TAG, "Retrieving preference Boolean '" + key + "' via IPC");
				
				return mService.getBoolean(key, defaultValue);
				
			} else if (!mData.containsKey(key)) {
				mData.put(key, mService.getBoolean(key, defaultValue));
			}
			
			if(Common.debug()) Log.d(TAG, "Retrieving preference Boolean '" + key + "' from Cache");
			
			return (Boolean) mData.get(key);
			
		} catch (RemoteException e) { handleRemoteException(e); return defaultValue; }
	}

	public String getString(String key) {
		return getString(key, null);
	}
	
	@SuppressWarnings("unchecked")
	public List<String> getStringArray(String key, ArrayList<String> defaultValue) {
		try {
			if (!mUseCache) {
				if(Common.debug()) Log.d(TAG, "Retrieving preference StringArray '" + key + "' via IPC");
				
				return mService.getStringArray(key, defaultValue);
				
			} else if (!mData.containsKey(key)) {
				mData.put(key, mService.getStringArray(key, defaultValue));
			}
			
			if(Common.debug()) Log.d(TAG, "Retrieving preference StringArray '" + key + "' from Cache");
			
			return (List<String>) mData.get(key);
			
		} catch (RemoteException e) { handleRemoteException(e); return defaultValue; }
	}
	
	public String getString(String key, String defaultValue) {
		try {
			if (!mUseCache) {
				if(Common.debug()) Log.d(TAG, "Retrieving preference String '" + key + "' via IPC");
				
				return mService.getString(key, defaultValue);
				
			} else if (!mData.containsKey(key)) {
				mData.put(key, mService.getString(key, defaultValue));
			}
			
			if(Common.debug()) Log.d(TAG, "Retrieving preference String '" + key + "' from Cache");
			
			return (String) mData.get(key);
			
		} catch (RemoteException e) { handleRemoteException(e); return defaultValue; }
	}

	public void putInt(String key, Integer value, Boolean preserve) {
		try {
			mService.putInt(key, value, preserve ? 1 : 0);
			
		} catch (RemoteException e) { handleRemoteException(e); }
	}
	
	public void putInt(String key, Integer value) {
		try {
			mService.putInt(key, value, -1);
			
		} catch (RemoteException e) { handleRemoteException(e); }
	}

	public void putBoolean(String key, Boolean value, Boolean preserve) {
		try {
			mService.putBoolean(key, value, preserve ? 1 : 0);
			
		} catch (RemoteException e) { handleRemoteException(e); }
	}
	
	public void putBoolean(String key, Boolean value) {
		try {
			mService.putBoolean(key, value, -1);
			
		} catch (RemoteException e) { handleRemoteException(e); }
	}

	public void putString(String key, String value, Boolean preserve) {
		try {
			mService.putString(key, value, preserve ? 1 : 0);
			
		} catch (RemoteException e) { handleRemoteException(e); }
	}
	
	public void putString(String key, String value) {
		try {
			mService.putString(key, value, -1);
			
		} catch (RemoteException e) { handleRemoteException(e); }
	}
	
	public void putStringArray(String key, ArrayList<String> value, Boolean preserve) {
		try {
			mService.putStringArray(key, value == null ? new ArrayList<String>() : value, preserve ? 1 : 0);
			
		} catch (RemoteException e) { handleRemoteException(e); }
	}
	
	public void putStringArray(String key, ArrayList<String> value) {
		try {
			mService.putStringArray(key, value == null ? new ArrayList<String>() : value, -1);
			
		} catch (RemoteException e) { handleRemoteException(e); }
	}
	
	public boolean remove(String key) {
		try {
			return mService.remove(key);
			
		} catch (RemoteException e) { handleRemoteException(e); }
		
		return false;
	}
	
	public List<String> getPreservedKeys() {
		try {
			return mService.getPreservedKeys();
			
		} catch (RemoteException e) { handleRemoteException(e); }
		
		return null;
	}
	
	public int getType(String key) {
		try {
			return mService.getType(key);
			
		} catch (RemoteException e) { handleRemoteException(e); }
		
		return -1;
	}
	
	public List<String> getKeys() {
		try {
			return mService.getKeys();
			
		} catch (RemoteException e) { handleRemoteException(e); }
		
		return null;
	}
	
	public boolean apply() {
		try {
			return mService.apply();
			
		} catch (RemoteException e) { handleRemoteException(e); }
		
		return false;
	}
	
	public void commit() {
		try {
			mService.commit();
			
		} catch (RemoteException e) { handleRemoteException(e); }
	}
	
	public Boolean isPackageUnlocked() {
		if (!mUseCache || mIsUnlocked == null) {
			try {
				mIsUnlocked = mService.isUnlocked();
			
			} catch (RemoteException e) {
				mIsUnlocked = false;
				handleRemoteException(e);
			}
		}
		
		return mIsUnlocked;
	}
	
	public Boolean isServiceReady() {
		if (isReady == null) {
			try {
				if (mService.isReady()) {
					isReady = true;
				}
			
			} catch (RemoteException e) {
				handleRemoteException(e);
			}
		}
		
		return isReady != null && isReady;
	}
	
	public Integer getVersion() {
		try {
			return mService.getVersion();
		
		} catch (RemoteException e) {
			handleRemoteException(e);
		}
		
		return 0;
	}
}
