package com.spazedog.xposed.additionsgb;

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
import android.widget.Button;

public class DialogBroadcastReceiver {
	
	protected Dialog mDialog;
	protected OnDialogListener mListener;
	protected Boolean mPositive = false;
	protected Boolean mBound = false;
	protected IntentFilter mIntentFilter;
	protected BroadcastReceiver mBroadcastReceiver;
	
	public static interface OnDialogListener {
		public abstract void OnClose(DialogBroadcastReceiver dialog, Boolean positive);
		public abstract void OnOpen(DialogBroadcastReceiver dialog);
		public abstract void OnReceive(DialogBroadcastReceiver dialog, Intent intent);
	}
	
	public DialogBroadcastReceiver(Activity activity, Integer layout) {
		mBroadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (mListener != null) {
					mListener.OnReceive(DialogBroadcastReceiver.this, intent);
				}
			}
		};
		
		mDialog = new Dialog(activity);
		mDialog.setOwnerActivity(activity);
		mDialog.setCancelable(false);
		mDialog.setContentView(layout);
		mDialog.setOnDismissListener(new OnDismissListener(){
			@Override
			public void onDismiss(DialogInterface dialog) {
				if (mListener != null) {
					mListener.OnClose(DialogBroadcastReceiver.this, mPositive);
				}
			}
		});
		mDialog.setOnShowListener(new OnShowListener(){
			@Override
			public void onShow(DialogInterface arg0) {
				if (mListener != null) {
					mListener.OnOpen(DialogBroadcastReceiver.this);
				}
			}
		});
		
        ((Button) mDialog.findViewById(R.id.button_okay)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	mPositive = true;
            	mDialog.dismiss();
            }
        });
        
        ((Button) mDialog.findViewById(R.id.button_cancel)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	mDialog.dismiss();
            }
        });
	}
	
	public void setTitle(String text) {
		mDialog.setTitle(text);
	}
	
	public void setTitle(Integer resId) {
		mDialog.setTitle(resId);
	}
	
	public void setBroadcastIntent(IntentFilter intentFilter) {
		mIntentFilter = intentFilter;
	}
	
	public void setOnDialogListener(OnDialogListener listener) {
		mListener = listener;
	}
	
	public Dialog getDialog() {
		return mDialog;
	}
	
	public void open() {
		mDialog.show();
	}
	
	public void bind() {
		if (!mBound && mDialog.getContext() != null) {
			mDialog.getContext().registerReceiver(mBroadcastReceiver, mIntentFilter);
			mBound = true;
		}
	}
	
	public void unbind() {
		if (mBound && mDialog.getContext() != null) {
			try {
				mDialog.getContext().unregisterReceiver(mBroadcastReceiver);
				mBound = false;
			
			} catch (IllegalArgumentException e) {}
		}
	}
	
	public void destroy() {
		if (mDialog != null) {
			unbind();
			
			if (mDialog.isShowing()) {
				mDialog.dismiss();
			}
		}

		mDialog = null;
		mBroadcastReceiver = null;
		mIntentFilter = null;
		mListener = null;
	}
}
