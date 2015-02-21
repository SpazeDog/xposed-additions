package com.spazedog.xposed.additionsgb;

import java.io.IOException;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

@TargetApi(Build.VERSION_CODES.HONEYCOMB) // API 11
public class ServiceTorch extends Service {
	
	private Camera mCamera;
	private WakeLock mWakeLock;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && Common.TORCH_INTENT_ACTION.equals(intent.getAction())) {
			if (mCamera != null) {
				torchOff();
				
			} else {
				torchOn();
			}
			
			return START_REDELIVER_INTENT;
		}

		return START_NOT_STICKY;
	}
	
	@Override
	public void onDestroy() {
		if (mCamera != null) {
			torchOff();
		}
		
		super.onDestroy();
	}
	
	private void torchOn() {
		mCamera = Camera.open();
		
		try {
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TorchLock");
			mWakeLock.acquire();
			
			Parameters params = mCamera.getParameters();
			params.setFlashMode(Parameters.FLASH_MODE_TORCH);
			
			mCamera.setParameters(params);
			mCamera.setPreviewTexture(new SurfaceTexture(0));
			mCamera.startPreview();
			
		} catch (IOException e) {
			torchOff();
		}
	}
	
	private void torchOff() {
		Parameters params = mCamera.getParameters();
		params.setFlashMode(Parameters.FLASH_MODE_OFF);
		
		mCamera.setParameters(params);
		mCamera.stopPreview();
		mCamera.release();
		mCamera = null;
		
		if (mWakeLock != null && mWakeLock.isHeld()) {
			mWakeLock.release();
		}
		
		stopSelf();
	}
}
