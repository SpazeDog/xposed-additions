package com.spazedog.xposed.additionsgb.hooks.tools;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;

import com.spazedog.xposed.additionsgb.Common;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * This is a Base class hook for XPosedBridge. 
 * It's purpose it the same as XC_MethodHook, only this makes it
 * much easier to extend large parts of a class rather than just one single method in it,
 * and communicate between different internal and extended methods, share properties etc.
 * 
 * TODO: Make it possible for inner classes to access outer class methods and properties
 */
public abstract class XC_ClassHook extends XC_MethodHook {
	
	private static final String _TAG = Common.PACKAGE_NAME + "$XC_ClassHook";
	
    public final static boolean SDK_GB = android.os.Build.VERSION.SDK_INT <= 10;
    public final static boolean SDK_ICS = android.os.Build.VERSION.SDK_INT > 10 && android.os.Build.VERSION.SDK_INT < 16;
    public final static boolean SDK_JB = android.os.Build.VERSION.SDK_INT > 15;

	private ClassLoader mClassLoader;
	
	private Class<?> mClassType;
	
	private WeakReference<Object> mClassObject;
	
	Map<String, Method> mClassMethods = new HashMap<String, Method>();
	Map<String, Field> mClassProperties = new HashMap<String, Field>();
	
	Map<String, Method> mHookMethods = new HashMap<String, Method>();
	Map<String, Integer> mHookTypes = new HashMap<String, Integer>();
	
	public XC_ClassHook(String className, ClassLoader classLoader) {
		if (Common.DEBUG) {
			Log.d(_TAG, "Starting class hook implementation (" + this.getClass().getName() + ")");
		}
		
		mClassLoader = classLoader;
		mClassType = XposedHelpers.findClass(className, classLoader);
		
		Class<?> current = mClassType;
		do {
			Method[] methods = current.getDeclaredMethods();
			Field[] fields = current.getDeclaredFields();
			
			for (int i=0; i < methods.length; i++) {
				if (methods[i] instanceof Method) {
					Boolean hook = false;
					Integer argCount = methods[i].getParameterTypes().length;
					String name = methods[i].getName() + ":" + argCount;
					String[] internals = i == 0 ? 
							new String[]{"xb_constructor", "xa_constructor", "xb_" + methods[i].getName(), "xa_" + methods[i].getName()} : 
								new String[]{"xb_" + methods[i].getName(), "xa_" + methods[i].getName()};
					
					mClassMethods.put(name, methods[i]);
					
					for (int x=0; x < internals.length; x++) {
						if (!mHookMethods.containsKey(internals[x])) {
							try {
								Method internal = getClass().getMethod(internals[x], new Class[]{MethodHookParam.class});
								
								if (internal != null) {
									if (Common.DEBUG) {
										Log.d(_TAG, "Setting up a new hook " + internals[x] + " (" + getClass().getName() + ")");
									}
									
									mHookMethods.put(internals[x], internal);
									
									/*
									 * Make sure that we do not hook a constructor. 
									 * These are already hooked. 
									 */
									if (i > 0 || x > 1) {
										hook = true;
									}
								}
								
							} catch (NoSuchMethodException e) {}
						}
					}
					
					if (hook) {
						XposedBridge.hookAllMethods(mClassType, methods[i].getName(), this);
					}
				}
			}
			
			for (int i=0; i < fields.length; i++) {
				fields[i].setAccessible(true);
				mClassProperties.put(fields[i].getName(), fields[i]);
			}
			
		} while ((current = current.getSuperclass()) != null);
		
		XposedBridge.hookAllConstructors(mClassType, this);
	}
	
	@Override
	protected final void beforeHookedMethod(final MethodHookParam param) {
		String methodName = null;
		
		if (param.method instanceof Constructor) {
			mClassObject = new WeakReference<Object>(param.thisObject);
			
			methodName = "xb_constructor";
			
		} else {
			methodName = "xb_" + param.method.getName();
		}

		if (mHookMethods.containsKey(methodName)) {
			if (Common.DEBUG) {
				Log.d(_TAG, "Running before hook on " + methodName + " (" + this.getClass().getName() + ")");
			}

			Method hook = mHookMethods.get(methodName);
			try {
				Object result = hook.invoke(this, param);
				
				if (result != null) {
					param.setResult(result);
				}
				
			} catch (IllegalArgumentException e) { e.printStackTrace();
			} catch (IllegalAccessException e) { e.printStackTrace();
			} catch (InvocationTargetException e) { e.printStackTrace(); }
		}
	}
	
	@Override
	protected final void afterHookedMethod(final MethodHookParam param) {
		String methodName = param.method instanceof Constructor ? 
				"xa_constructor" : 
					"xa_" + param.method.getName();

		if (mHookMethods.containsKey(methodName)) {
			if (Common.DEBUG) {
				Log.d(_TAG, "Running after hook on " + methodName + " (" + this.getClass().getName() + ")");
			}
			
			Method hook = mHookMethods.get(methodName);
			try {
				Object result = hook.invoke(this, param);
				
				if (result != null) {
					param.setResult(result);
				}
				
			} catch (IllegalArgumentException e) { e.printStackTrace();
			} catch (IllegalAccessException e) { e.printStackTrace();
			} catch (InvocationTargetException e) { e.printStackTrace(); }
		}
	}
	
	protected final ClassLoader getClassLoader() {
		return mClassLoader;
	}
	
	protected final Class<?> getClassType() {
		return mClassType;
	}
	
	protected final Object getClassObject() {
		return mClassObject.get();
	}
	
	protected final Object invokeOriginalMethod(String methodName, Object... args) {
		String name = methodName + ":" + args.length;
		
		if (mClassMethods.containsKey(name)) {
			Method method = mClassMethods.get(name);
			
			try {
				return XposedBridge.invokeOriginalMethod(method, mClassObject.get(), args);
				
			} catch (NullPointerException e) { e.printStackTrace();
			} catch (IllegalArgumentException e) { e.printStackTrace();
			} catch (IllegalAccessException e) { e.printStackTrace();
			} catch (InvocationTargetException e) { e.printStackTrace(); }
		}
		
		return null;
	}
	
	protected final Object invokeMethod(String methodName, Object... args) {
		String name = methodName + ":" + args.length;
		
		if (mClassMethods.containsKey(name)) {
			Method method = mClassMethods.get(name);
			
			try {
				return method.invoke(mClassObject.get(), args);
				
			} catch (IllegalArgumentException e) { e.printStackTrace();
			} catch (IllegalAccessException e) { e.printStackTrace();
			} catch (InvocationTargetException e) { e.printStackTrace(); }
		}
		
		return null;
	}
	
	protected final Object invokeField(String fieldName, String methodName, Object... args) {
		Object field = getField(fieldName);
		
		Method[] methods = field.getClass().getDeclaredMethods();
		
		for (int i=0; i < methods.length; i++) {
			if (methods[i].getName().equals(methodName) && methods[i].getParameterTypes().length == args.length) {
				try {
					return methods[i].invoke(field, args);
					
				} catch (IllegalArgumentException e) { e.printStackTrace();
				} catch (IllegalAccessException e) { e.printStackTrace();
				} catch (InvocationTargetException e) { e.printStackTrace(); }
			}
		}
		
		return null;
	}
	
	protected final Object getField(String name) {
		if (mClassProperties.containsKey(name)) {
			Field field = mClassProperties.get(name);
			
			try {
				return field.get(mClassObject.get());
				
			} catch (IllegalArgumentException e) { e.printStackTrace();
			} catch (IllegalAccessException e) { e.printStackTrace(); }
		}
		
		return null;
	}
	
	protected final void setField(String name, Object value) {
		if (mClassProperties.containsKey(name)) {
			Field field = mClassProperties.get(name);
			
			try {
				field.set(mClassObject.get(), value);
				
			} catch (IllegalArgumentException e) { e.printStackTrace();
			} catch (IllegalAccessException e) { e.printStackTrace(); }
		}
	}
}
