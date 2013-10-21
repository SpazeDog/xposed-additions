package com.spazedog.xposed.additionsgb;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import com.spazedog.xposed.additionsgb.DialogBroadcastReceiver.OnDialogListener;

public class ActivityRemapSettings extends PreferenceActivity implements OnPreferenceClickListener {
	
	private Preference mPrefAddButton;
	private DialogBroadcastReceiver mDialog;
	
	private Boolean mUpdated = false;

	@Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	addPreferencesFromResource(R.xml.activity_remap_settings);
    	
    	Common.loadSharedPreferences(this);
    	
    	mPrefAddButton = (Preference) findPreference("add_button");
    	mPrefAddButton.setTitle(R.string.preference_title_add_button);
    	mPrefAddButton.setSummary(R.string.preference_summary_add_button);
    	mPrefAddButton.setOnPreferenceClickListener(this);
    	
    	Integer[] keyList = Common.Remap.getKeyList();
    	for (int i=0; i < keyList.length; i++) {
    		createKeyPreference(keyList[i]);
    	}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		if (mDialog != null && mDialog.getDialog() != null && mDialog.getDialog().isShowing()) {
			requestInterceptStart();
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		if (mDialog != null && mDialog.getDialog() != null && mDialog.getDialog().isShowing()) {
			requestInterceptStop();
		}
		
		if (mUpdated) {
			Common.requestConfigUpdate(this);
		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		
		if (mDialog != null) {
			mDialog.destroy();
			mDialog = null;
		}
	}

	@SuppressLint("NewApi")
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference == mPrefAddButton) {
			mDialog = new DialogBroadcastReceiver(this, R.layout.dialog_intercept_key);
			mDialog.setTitle(R.string.alert_dialog_title_intercept_key);
			mDialog.setBroadcastIntent(new IntentFilter(Common.BroadcastOptions.INTENT_ACTION_RESPONSE));
			mDialog.setOnDialogListener(new OnDialogListener(){
				
				private Integer mKeyCode;
				
				@Override
				public void OnClose(DialogBroadcastReceiver dialog, Boolean positive) {
					requestInterceptStop();
					
					dialog.destroy();
					dialog = null;
					
					if (positive) {
						String keyCode = "" + mKeyCode;
						SharedPreferences sharedPreferences = Common.getSharedPreferences(ActivityRemapSettings.this);
						String[] keyArray = sharedPreferences.getString(Common.Remap.KEY_COLLECTION, "").split(",");
						List<String> keyList = new ArrayList<String>();
						
						for (int i=0; i < keyArray.length; i++) {
							if (!"".equals(keyArray[i])) {
								keyList.add(keyArray[i]);
							}
						}
						
						if (!keyList.contains(keyCode)) {
							keyList.add(keyCode);
							
							sharedPreferences.edit().putString(Common.Remap.KEY_COLLECTION, TextUtils.join(",", keyList)).apply();
							
							createKeyPreference(mKeyCode);
							
						} else {
							Toast.makeText(ActivityRemapSettings.this, R.string.alert_dialog_title_key_exists, Toast.LENGTH_LONG).show();
						}
					}
				}
	
				@Override
				public void OnOpen(DialogBroadcastReceiver dialog) {
					if (mDialog == null) {
						mDialog = dialog;
					}
					
					((TextView) mDialog.getDialog().findViewById(R.id.content_name)).setText("Key Code");
					((TextView) mDialog.getDialog().findViewById(R.id.content_text)).setText("Press the key that you would like to add");
					
					dialog.bind();
					
					requestInterceptStart();
				}
	
				@Override
				public void OnReceive(DialogBroadcastReceiver dialog, Intent intent) {
					mKeyCode = intent.getIntExtra("keycode", 0);
					
					((TextView) mDialog.getDialog().findViewById(R.id.content_value)).setText("" + mKeyCode + " (" + Common.keycodeToString(mKeyCode) + ")");
				}
			});
			
			mDialog.open();
		
			return true;
			
		} else if (preference.getIntent() != null) {
			startActivityForResult(preference.getIntent(), preference.getIntent().getIntExtra("keycode", 0));
			
			return true;
		}
		
		return false;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode > 0) {
			String keyCode = "" + resultCode;
			SharedPreferences sharedPreferences = Common.getSharedPreferences(ActivityRemapSettings.this);
			String[] keyArray = sharedPreferences.getString(Common.Remap.KEY_COLLECTION, "").split(",");
			List<String> keyList = new ArrayList<String>();
			
			for (int i=0; i < keyArray.length; i++) {
				if (!"".equals(keyArray[i])) {
					keyList.add(keyArray[i]);
				}
			}
			
			if (keyList.contains(keyCode)) {
				keyList.remove(keyCode);
				
				Editor editor = sharedPreferences.edit();
				
				editor.putString(Common.Remap.KEY_COLLECTION, TextUtils.join(",", keyList));
				editor.remove(Common.Remap.KEY_OFF_ENABLED + keyCode);
				editor.remove(Common.Remap.KEY_OFF_ACTION_CLICK + keyCode);
				editor.remove(Common.Remap.KEY_OFF_ACTION_TAP + keyCode);
				editor.remove(Common.Remap.KEY_OFF_ACTION_PRESS + keyCode);
				editor.remove(Common.Remap.KEY_ON_ENABLED + keyCode);
				editor.remove(Common.Remap.KEY_ON_ACTION_CLICK + keyCode);
				editor.remove(Common.Remap.KEY_ON_ACTION_TAP + keyCode);
				editor.remove(Common.Remap.KEY_ON_ACTION_PRESS + keyCode);
				
				editor.apply();
				
				mUpdated = true;
				
				((PreferenceCategory) findPreference("category_buttons")).removePreference( findPreference("keycode/" + keyCode) );
				
				Toast.makeText(this, R.string.key_removed, Toast.LENGTH_LONG).show();
			}
		}
	}
	
	protected void createKeyPreference(Integer keyCode) {
		PreferenceCategory buttonCategory = (PreferenceCategory) findPreference("category_buttons");
		
    	Intent intent = new Intent();
    	intent.setAction( Intent.ACTION_VIEW );
    	intent.setClass(this, ActivityRemapButton.class);
    	intent.putExtra("keycode", (int) keyCode);
    	
    	Preference buttonPreference = new Preference(this);
    	buttonPreference.setKey("keycode/" + keyCode);
    	buttonPreference.setTitle(Common.keycodeToString(keyCode));
    	buttonPreference.setSummary("Key Code " + keyCode);
    	buttonPreference.setIntent(intent);
    	buttonPreference.setOnPreferenceClickListener(this);
    	
    	buttonCategory.addPreference(buttonPreference);
	}
	
	protected void requestInterceptStart() {
		Intent intent = new Intent(Common.BroadcastOptions.INTENT_ACTION_REQUEST);
		intent.putExtra("request", Common.BroadcastOptions.REQUEST_ENABLE_KEYCODE_INTERCEPT);
		
		sendBroadcast(intent);
	}
	
	protected void requestInterceptStop() {
		Intent intent = new Intent(Common.BroadcastOptions.INTENT_ACTION_REQUEST);
		intent.putExtra("request", Common.BroadcastOptions.REQUEST_DISABLE_KEYCODE_INTERCEPT);
		
		sendBroadcast(intent);
	}
}
