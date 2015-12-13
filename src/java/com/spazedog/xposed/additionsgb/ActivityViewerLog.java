package com.spazedog.xposed.additionsgb;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import com.spazedog.xposed.additionsgb.backend.LogcatMonitor;
import com.spazedog.xposed.additionsgb.backend.service.XServiceManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ActivityViewerLog extends Activity {

    private List<String> mLog;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mLog != null) {
            outState.putStringArrayList("mLog", (ArrayList<String>) mLog);
        }
    }

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.error_log_view);

        StringBuilder builder = new StringBuilder();

        if (savedInstanceState != null && savedInstanceState.containsKey("mLog")) {
            mLog = savedInstanceState.getStringArrayList("mLog");

        } else {
            XServiceManager manager = XServiceManager.getInstance();

            if (manager != null && manager.isServiceReady()) {
                mLog = manager.getLogEntries();

            } else {
                mLog = LogcatMonitor.buildLog();
            }
        }

        for (String entry : mLog) {
            if (builder.length() > 0) {
                builder.append("\n");
            }

            builder.append(entry);
        }

        TextView view = (TextView) findViewById(R.id.content);
        view.setText(builder);
		
		if (Build.VERSION.SDK_INT >= 14) {
			Toolbar bar = (Toolbar) findViewById(R.id.toolbar);
			bar.setTitle(R.string.category_title_logviewer);
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
		File file = new File(getCacheDir(), "error.log");
		
		try {
			TextView view = (TextView) findViewById(R.id.content);
			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file, false));
			fileWriter.write( (String) view.getText() );
			fileWriter.close();
			
		} catch (IOException e) {}
		
		switch (item.getItemId()) {
			case R.id.menu_edit: 
				if (file.exists()) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setDataAndType(Uri.parse("file://" + file.getCanonicalPath()), "text/plain");
						
						startActivity(Intent.createChooser(intent, getResources().getString(R.string.menu_title_edit)));
						
					} catch (IOException e) {}
				}
				
				return true;
				
			case R.id.menu_send: 
				if (file.exists()) {
					try {
						Intent intent = new Intent(Intent.ACTION_SEND);
						intent.putExtra(Intent.EXTRA_EMAIL, new String[] {"d.bergloev@gmail.com"});
						intent.putExtra(Intent.EXTRA_SUBJECT, "XposedAdditions: Error Log");
						intent.putExtra(Intent.EXTRA_TEXT, getDeviceInfo());
						intent.setType("text/plain");
						intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file.getCanonicalPath()));
						
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
