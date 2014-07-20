package com.spazedog.xposed.additionsgb;

import java.io.IOException;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Build;
import android.os.IBinder;

@TargetApi(Build.VERSION_CODES.HONEYCOMB) // API 11
public class ServiceTorch extends Service {
	
	private Camera mCamera;
	
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
		
		stopSelf();
	}
}
