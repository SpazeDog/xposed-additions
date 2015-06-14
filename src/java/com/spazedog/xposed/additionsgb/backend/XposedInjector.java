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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;

import android.os.Build;
import android.util.Log;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectMethod;
import com.spazedog.lib.reflecttools.utils.ReflectConstants.Match;
import com.spazedog.xposed.additionsgb.Common;
import com.spazedog.xposed.additionsgb.backend.pwm.PhoneWindowManager;
import com.spazedog.xposed.additionsgb.backend.service.XService;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public final class XposedInjector implements IXposedHookZygoteInit, IXposedHookLoadPackage {
	@Override
	public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
		LogcatMonitor.init();

		XService.init();
		ApplicationLayout.init();
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (lpparam.packageName.equals("android")) {
			/*
			 * In API 21, the boot class loader no longer has access to internal services.
			 */
			if (Build.VERSION.SDK_INT >= 21) {
				ReflectClass.setClassLoader(lpparam.classLoader);
			}
			
			PowerManager.init();
			PhoneWindowManager.init();
			InputManager.init();
		}
	}
	
	public static class LogcatMonitor {
		
		private static ReflectClass mLogUtil;
		private static volatile Object mLock = new Object();
		
		private final static int VERBOSE = 2;
		private final static int DEBUG = 3;
		private final static int INFO = 4;
		private final static int WARNING = 5;
		private final static int ERROR = 6;
		private final static int ASSERT = 7;
		
		private final static Map<Integer, String> LEVELS = new HashMap<Integer, String>();
		static {
			LEVELS.put(VERBOSE, "V");
			LEVELS.put(DEBUG, "D");
			LEVELS.put(INFO, "I");
			LEVELS.put(WARNING, "W");
			LEVELS.put(ERROR, "E");
			LEVELS.put(ASSERT, "A");
		}
		
		public static void init() {
			try {
				Log.d(LogcatMonitor.class.getName(), "Loading Logcat Monitor!!!");
				
				File cacheDir = Common.LogFile.MAIN.getParentFile();
				ReflectMethod setPermissions = ReflectClass.forName("android.os.FileUtils").findMethod("setPermissions", Match.BEST, String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE);
				
				if (cacheDir.exists() || cacheDir.mkdir()) {
					setPermissions.invoke(cacheDir.getPath(), 0777, -1, -1);
				}

				File[] files = new File[]{Common.LogFile.MAIN, Common.LogFile.STORED};

				for (File file : files) {
					if ((!file.exists() || file.delete()) && file.createNewFile()) {
						setPermissions.invoke(file.getPath(), 0666, -1, -1);
					}
				}
				
				if (Common.LogFile.MAIN.exists()) {
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
				
				if (priority == ERROR) {
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
		
		protected void writeLog(final Integer priority, final String tag, final String message, final Integer count) {
			if (!tag.endsWith("#NoLoop")) {
				new Thread() {
					@Override
					public void run() {
						synchronized(mLock) {
							for (int i=500; i > 0; i--) {
								Long bytes = Common.LogFile.MAIN.length();
								Boolean append = bytes > 0 && bytes <= (Common.LogFile.SIZE / 2);
								FileChannel channel = null;
								FileLock lock = null;
								BufferedWriter fileWriter = null;
								
								try {
									channel = new RandomAccessFile(Common.LogFile.LOCK, "rw").getChannel();
									lock = channel.lock();
									
									if (!append) {
										/*
										 * Instead of using a truncate technique, it is faster using two files instead.
										 */
										BufferedReader fileReader = null;
										
										try {
											String line;
											fileReader = new BufferedReader(new FileReader(Common.LogFile.MAIN));
											fileWriter = new BufferedWriter(new FileWriter(Common.LogFile.STORED, false));
											
											while ((line = fileReader.readLine()) != null) {
												fileWriter.append(line);
												fileWriter.append("\r\n");
											}
											
										} catch (Throwable ignorer) {} finally {
											if (fileReader != null) fileReader.close();
											if (fileWriter != null) fileWriter.close();
										}
									}
									
									fileWriter = new BufferedWriter(new FileWriter(Common.LogFile.MAIN, append));
									
									if (append) {
										fileWriter.append("------------------------\r\n");
									}
									
									fileWriter.append(LEVELS.get(priority) + "/");
									fileWriter.append(tag);
									fileWriter.append("\r\n\t");
									fileWriter.append(message.replace("\n", "\r\n\t\t"));
									fileWriter.append("\r\n");
									
								} catch (Throwable e) {
									try {
										Thread.sleep(10); continue;
										
									} catch (InterruptedException ignorer) {}
									
								} finally {
									try {
										if (fileWriter != null) fileWriter.close();
										if (channel != null) channel.close();
										if (lock != null) lock.release();
										
									} catch (Throwable e) {}
								}
								
								break;
							}
						}
					}
					
				}.start();
			}
		}
	}
}
