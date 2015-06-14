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

package com.spazedog.xposed.additionsgb.tools;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnShowListener;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;

import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager.XServiceBroadcastListener;

public abstract class DialogBroadcastReceiver {
	
	private Dialog mDialog;
	private Boolean mPositive = false;
	private Boolean mBound = false;
	private Handler mHandler = new Handler();
	
	private XServiceManager mManager;
	
	private XServiceBroadcastListener mBroadcastReceiver = new XServiceBroadcastListener() {
		@Override
		public void onBroadcastReceive(final String action, final Bundle data) {
			mHandler.post(new Runnable(){
				@Override
				public void run() {
					DialogBroadcastReceiver.this.onReceive(action, data);
				}
			});
		}
	};
	
	private OnShowListener mOnShowListener = new OnShowListener(){
		@Override
		public void onShow(DialogInterface dialog) {
			bind();
			onOpen();
		}
	};
	
	private OnDismissListener mOnDismissListener = new OnDismissListener(){
		@Override
		public void onDismiss(DialogInterface dialog) {
			unbind();
			onClose(mPositive);
			
			mDialog = null;
		}
	};
	
	private OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			mPositive = v.getId() == R.id.button_okay;
			mDialog.dismiss();
		}
	};
	
	public void open(Activity activity, Integer layoutRes) {
		if (mDialog == null || !mDialog.isShowing()) {
			if (mDialog != null) {
				close();
			}
			
			mManager = XServiceManager.getInstance();
			
			mDialog = new Dialog(activity);
			mDialog.setOwnerActivity(activity);
			mDialog.setCancelable(false);
			mDialog.setContentView(layoutRes);
			mDialog.setOnDismissListener(mOnDismissListener);
			mDialog.setOnShowListener(mOnShowListener);
			mDialog.findViewById(R.id.button_okay).setOnClickListener(mOnClickListener);
			mDialog.findViewById(R.id.button_cancel).setOnClickListener(mOnClickListener);
			mDialog.show();
		}
	}
	
	public void close() {
		mPositive = false;
		
		if (mDialog != null) {
			if (mDialog.isShowing()) {
				mDialog.dismiss();
			}
			
			mDialog = null;
		}
	}
	
	public void bind() {
		if (!mBound && mDialog != null && mDialog.isShowing()) {
			mManager.addBroadcastListener(mBroadcastReceiver);
			mBound = true;
			
			onBind();
		}
	}
	
	public void unbind() {
		if (mBound && mDialog != null) {
			try {
				mManager.removeBroadcastListener(mBroadcastReceiver);
				mBound = false;
			
			} catch (IllegalArgumentException e) {}
			
			onUnbind();
		}
	}
	
	public Dialog getWindow() {
		return mDialog;
	}
	
	public Boolean isOpen() {
		return mDialog != null;
	}
	
	protected void onBind() {}
	protected void onUnbind() {}
	protected void onClose(Boolean positive) {}
	protected void onOpen() {}
	protected abstract void onReceive(String action, Bundle data);
}
