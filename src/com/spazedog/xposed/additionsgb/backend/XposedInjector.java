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

package com.spazedog.xposed.additionsgb.backend;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import android.util.Log;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectMethod;
import com.spazedog.lib.reflecttools.utils.ReflectMember.Match;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.backend.service.XService;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;

public final class XposedInjector implements IXposedHookZygoteInit {
	@Override
	public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
		/*
		 * Register our logcat monitor. 
		 * This should be the first to be activated so that
		 * logging is on before anything else starts.
		 */
		LogcatMonitor.init();
		
		/*
		 * Register our custom system service
		 */
		XService.init();
		
		/*
		 * Register Hooks
		 */
		PowerManager.init();
		ApplicationLayout.init();
		PhoneWindowManager.init();
		InputManager.init();
	}
	
	public static class LogcatMonitor {
		
		private static ReflectClass mLogUtil;
		
		private final static int WARNING = 5;
		private final static int ERROR = 6;
		
		public static void init() {
			try {
				File cacheDir = Common.LOG_FILE.getParentFile();
				ReflectMethod setPermissions = ReflectClass.forName("android.os.FileUtils").findMethod("setPermissions", Match.BEST, String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE);
				
				if (cacheDir.exists() || cacheDir.mkdir()) {
					setPermissions.invoke(cacheDir.getPath(), 0777, -1, -1);
				}

				if ((!Common.LOG_FILE.exists() || Common.LOG_FILE.delete()) && Common.LOG_FILE.createNewFile()) {
					setPermissions.invoke(Common.LOG_FILE.getPath(), 0777, -1, -1);
				}
				
				if (Common.LOG_FILE.exists()) {
					LogcatMonitor monitor = new LogcatMonitor();
					
					mLogUtil = ReflectClass.forName("android.util.Log");
					mLogUtil.inject("println_native", monitor.hook_errorAndWarning);
				}
				
			} catch (Throwable e) {
				Log.e(Common.PACKAGE_NAME, e.getMessage(), e);
			}
		}
		
		protected XC_MethodHook hook_errorAndWarning = new XC_MethodHook() {
			@Override
			protected final void beforeHookedMethod(final MethodHookParam param) {
				Integer priority = (Integer) param.args[1];
				
				if (priority == WARNING || priority == ERROR) {
					String tag = (String) param.args[2];
					
					if (!tag.equals(Common.PACKAGE_NAME + "#NoLoop")) {
						try {
							String message = (String) param.args[3];
							
							if (tag.contains(Common.PACKAGE_NAME) || message.contains(Common.PACKAGE_NAME)) {
								writeLog(priority, tag, message, 0);
							}
							
						} catch (Throwable e) {
							Log.e(Common.PACKAGE_NAME + "#NoLoop", e.getMessage(), e);
						}
					}
				}
			}
		};
		
		protected synchronized void writeLog(Integer priority, String tag, String message, Integer count) {
			FileChannel channel = null;
			FileLock lock = null;
			BufferedWriter fileWriter = null;
			Long bytes = Common.LOG_FILE.length();
			Boolean append = bytes > 0 && bytes <= Common.LOG_SIZE;
			
			try{
				channel = new RandomAccessFile(Common.LOG_FILE, "rw").getChannel();
				lock = channel.lock();
				fileWriter = new BufferedWriter(new FileWriter(Common.LOG_FILE, append));
				
				if (append) {
					fileWriter.append("------------------------\r\n");
				}
				
				fileWriter.append(priority == WARNING ? "W/" : "E/");
				fileWriter.append(tag);
				fileWriter.append("\r\n\t");
				fileWriter.append(message.replace("\n", "\r\n\t\t"));
				fileWriter.append("\r\n");
				
			} catch(Throwable e) {
				if (count < 5) {
					try {
						Thread.sleep(200);
						
						writeLog(priority, tag, message, count+1);
						
					} catch (Throwable ei) {
						Log.e(Common.PACKAGE_NAME + "#NoLoop", e.getMessage(), e);
					}
					
				} else {
					Log.e(Common.PACKAGE_NAME + "#NoLoop", e.getMessage(), e);
				}
				
			} finally {
				try {
					if (fileWriter != null) fileWriter.close();
					if (lock != null) lock.release();
					if (channel != null) channel.close();
					
				} catch (IOException e) {
					Log.e(Common.PACKAGE_NAME + "#NoLoop", e.getMessage(), e);
				}
			}
		}
	}
}
