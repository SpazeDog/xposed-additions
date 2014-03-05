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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.view.View.OnClickListener;

import com.spazedog.xposed.additionsgb.R;

public abstract class DialogBroadcastReceiver {
	
	private Dialog mDialog;
	private Boolean mPositive = false;
	private Boolean mBound = false;
	private IntentFilter mIntentFilter;
	
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			DialogBroadcastReceiver.this.onReceive(intent);
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
	
	public void open(Activity activity, Integer layoutRes, IntentFilter intentFilter) {
		if (mDialog == null || !mDialog.isShowing()) {
			if (mDialog != null) {
				close();
			}
			
			mIntentFilter = intentFilter;
			
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
		if (!mBound && mDialog != null && mDialog.isShowing() && mDialog.getContext() != null) {
			mDialog.getContext().registerReceiver(mBroadcastReceiver, mIntentFilter);
			mBound = true;
			
			onBind();
		}
	}
	
	public void unbind() {
		if (mBound && mDialog != null && mDialog.getContext() != null) {
			try {
				mDialog.getContext().unregisterReceiver(mBroadcastReceiver);
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
	protected abstract void onReceive(Intent intent);
}
