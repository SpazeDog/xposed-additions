package com.spazedog.xposed.additionsgb;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

public class ActivityViewerLog extends Activity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.error_log_view);
		
		if (Common.LOG_FILE.exists()) {
			BufferedReader reader = null;
			
			try {
				TextView view = (TextView) findViewById(R.id.content);
				reader = new BufferedReader(new FileReader(Common.LOG_FILE));
				StringBuilder builder = new StringBuilder();
				String line;
				
				while ((line = reader.readLine()) != null) {
					builder.append(line);
					builder.append("\n");
				}
				
				view.setText( builder.toString() );
				
			}  catch (IOException e) {} finally {
				try {
					reader.close();
					
				} catch (IOException e) {}
			}
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_edit: 

				if (Common.LOG_FILE.exists()) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setDataAndType(Uri.parse("file://" + Common.LOG_FILE.getCanonicalPath()), "text/plain");
						
						startActivity(Intent.createChooser(intent, getResources().getString(R.string.menu_title_edit)));
						
					} catch (IOException e) {}
				}
				
				return true;
				
			case R.id.menu_send: 
				if (Common.LOG_FILE.exists()) {
					try {
						Intent intent = new Intent(Intent.ACTION_SEND);
						intent.putExtra(Intent.EXTRA_EMAIL, new String[] {"d.bergloev@gmail.com"});
						intent.putExtra(Intent.EXTRA_SUBJECT, "XposedAdditions: Error Log");
						intent.putExtra(Intent.EXTRA_TEXT, getDeviceInfo());
						intent.setType("text/plain");
						intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + Common.LOG_FILE.getCanonicalPath()));
						
						startActivity(Intent.createChooser(intent, getResources().getString(R.string.menu_title_send)));
					
					} catch (IOException e) {}
				}
				
				return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	protected String getDeviceInfo() {
		StringBuilder builder = new StringBuilder();
		
		Integer versionCode = 0;
		String versionName = "";
		
		try {
			PackageInfo info = getPackageManager().getPackageInfo(Common.PACKAGE_NAME, 0);
			
			versionCode = info.versionCode;
			versionName = info.versionName;
			
		} catch (NameNotFoundException e) {}
		
		builder.append("Module Version: (" + versionCode + ") " + versionName + "\r\n");
		builder.append("-----------------\r\n");
		builder.append("Manufacturer: " + Build.MANUFACTURER + "\r\n");
		builder.append("Brand: " + Build.BRAND + "\r\n");
		builder.append("Device: " + Build.DEVICE + "\r\n");
		builder.append("Module: " + Build.MODEL + "\r\n");
		builder.append("Product: " + Build.PRODUCT + "\r\n");
		builder.append("Software: (" + Build.VERSION.SDK_INT + ") " + Build.VERSION.RELEASE + "\r\n");
		builder.append("-----------------\r\n");
		
		return builder.toString();
	}
}
