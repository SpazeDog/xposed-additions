package com.spazedog.xposed.additionsgb.tools;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError;

/*
 * This is an extension of XposedHelpers.
 * It forces better tracking and handling of exceptions and it's 
 * class tools also includes SuperClass methods in cases where a class has been extended.
 * Also, the class tools is better optimized in speed as it does not convert argument object to string to use for caching. 
 * It is not as precise, but it will due in most cases. 
 * 
 * Also it provides a base which we can always change when needed.
 */

public class XposedTools {
	
	private static final HashMap<String, Method> methodCache = new HashMap<String, Method>();
	
	public static Constructor<?> findConstructor(Class<?> clazz, Class<?>... parameterTypes) throws NoSuchMethodError {
		return _findConstructor(clazz, parameterTypes, null);
	}
	
	public static Constructor<?> findConstructor(Class<?> clazz, Class<?>[] parameterTypes, Object... args) throws NoSuchMethodError {
		return _findConstructor(clazz, parameterTypes, args);
	}
	
	public static Constructor<?> findConstructor(Class<?> clazz, Object... args) throws NoSuchMethodError {
		return _findConstructor(clazz, null, args);
	}
	
	private static Constructor<?> _findConstructor(Class<?> clazz, Class<?>[] parameterTypes, Object[] args) throws NoSuchMethodError {
		if (parameterTypes == null) {
			return XposedHelpers.findConstructorBestMatch(clazz, args);
			
		} else if (args != null) {
			return XposedHelpers.findConstructorBestMatch(clazz, parameterTypes, args);
			
		} else {
			return XposedHelpers.findConstructorBestMatch(clazz, parameterTypes);
		}
	}

	public static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodError {
		return _findMethod(clazz, methodName, parameterTypes, null);
	}
	
	public static Method findMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object... args) throws NoSuchMethodError {
		return _findMethod(clazz, methodName, parameterTypes, args);
	}
	
	public static Method findMethod(Class<?> clazz, String methodName, Object... args) throws NoSuchMethodError {
		return _findMethod(clazz, methodName, null, args);
	}

	private static Method _findMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object[] args) throws NoSuchMethodError {
		String fullMethodName = clazz.getName() + "$" + methodName + "(" + (parameterTypes != null ? parameterTypes.length : args.length) + ")";
        
        if (methodCache.containsKey(fullMethodName)) {
            Method method = methodCache.get(fullMethodName);
            
            if (method == null)
                    throw new NoSuchMethodError(fullMethodName);
            
            return method;
        }
        
        Class<?> currentClazz = clazz;
        Method foundMethod = null;
        do {
        	try {
        		if (parameterTypes == null) {
        			foundMethod = XposedHelpers.findMethodBestMatch(currentClazz, methodName, args);
        			
        		} else if (args != null) {
        			foundMethod = XposedHelpers.findMethodBestMatch(currentClazz, methodName, parameterTypes, args);
        			
        		} else {
        			foundMethod = XposedHelpers.findMethodBestMatch(currentClazz, methodName, parameterTypes);
        		}
        		
        		break;
        		
        	} catch (NoSuchMethodError e) {}
        	
        } while (!currentClazz.equals(currentClazz.getSuperclass()) && (currentClazz = currentClazz.getSuperclass()) != null);
        
        if (foundMethod != null) {
        	methodCache.put(fullMethodName, foundMethod);
        	
        	return foundMethod;
        	
        } else {
        	NoSuchMethodError e = new NoSuchMethodError(fullMethodName);
        	methodCache.put(fullMethodName, null);
        	
        	throw e;
        }
	}
	
	public static Object callConstructor(Class<?> clazz, Object... args) throws NoSuchMethodError {
		try {
			return findConstructor(clazz, args).newInstance(args);
			
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getCause());
			
		} catch (IllegalAccessException e) {
			throw new IllegalAccessError(e.getMessage());
			
		} catch (InvocationTargetException e) {
			throw new XposedHelpers.InvocationTargetError(e.getCause());
			
		} catch (InstantiationException e) {
            throw new InstantiationError(e.getMessage());
		}
	}
	
	public static Object callConstructor(Class<?> clazz, Class<?>[] parameterTypes, Object... args) throws NoSuchMethodError {
		try {
			return findConstructor(clazz, parameterTypes, args).newInstance(args);
				
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getCause());
			
		} catch (IllegalAccessException e) {
			throw new IllegalAccessError(e.getMessage());
			
		} catch (InvocationTargetException e) {
			throw new XposedHelpers.InvocationTargetError(e.getCause());
			
		} catch (InstantiationException e) {
            throw new InstantiationError(e.getMessage());
		}
	}
	
	public static Object callMethod(Object obj, String methodName, Object... args) throws NoSuchMethodError {
		try {
			return findMethod(obj.getClass(), methodName, args).invoke(obj, args);
			
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getCause());
			
		} catch (IllegalAccessException e) {
			throw new IllegalAccessError(e.getMessage());
			
		} catch (InvocationTargetException e) {
			throw new XposedHelpers.InvocationTargetError(e.getCause());
		}
	}
	
	public static Object callMethod(Object obj, String methodName, Class<?>[] parameterTypes, Object... args) throws NoSuchMethodError {
		try {
			return findMethod(obj.getClass(), methodName, parameterTypes, args).invoke(obj, args);
			
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getCause());
			
		} catch (IllegalAccessException e) {
			throw new IllegalAccessError(e.getMessage());
			
		} catch (InvocationTargetException e) {
			throw new XposedHelpers.InvocationTargetError(e.getCause());
		}
	}
	
	public static Object callMethod(Class<?> clazz, String methodName, Object... args) throws NoSuchMethodError {
		try {
			return findMethod(clazz, methodName, args).invoke(null, args);
			
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getCause());
			
		} catch (IllegalAccessException e) {
			throw new IllegalAccessError(e.getMessage());
			
		} catch (InvocationTargetException e) {
			throw new XposedHelpers.InvocationTargetError(e.getCause());
		}
	}
	
	public static Object callMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object... args) throws NoSuchMethodError {
		try {
			return findMethod(clazz, methodName, parameterTypes, args).invoke(null, args);
				
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getCause());
			
		} catch (IllegalAccessException e) {
			throw new IllegalAccessError(e.getMessage());
			
		} catch (InvocationTargetException e) {
			throw new XposedHelpers.InvocationTargetError(e.getCause());
		}
	}
	
	public static Object getField(Object obj, String fieldName) throws NoSuchFieldError {
		try {
			return XposedHelpers.findField(obj.getClass(), fieldName).get(obj);

		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getCause());
			
		} catch (IllegalAccessException e) {
			throw new IllegalAccessError(e.getMessage());
		}
	}
	
	public static Object getField(Class<?> clazz, String fieldName) throws NoSuchFieldError {
		try {
			return XposedHelpers.findField(clazz, fieldName).get(null);
			
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getCause());
			
		} catch (IllegalAccessException e) {
			throw new IllegalAccessError(e.getMessage());
		}
	}

	public static void setField(Object obj, String fieldName, Object value) throws NoSuchFieldError {
		try {
			XposedHelpers.findField(obj.getClass(), fieldName).set(obj, value);
			
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getCause());
			
		} catch (IllegalAccessException e) {
			throw new IllegalAccessError(e.getMessage());
		}
	}
	
	public static void setField(Class<?> clazz, String fieldName, Object value) throws NoSuchFieldError {
		try {
			XposedHelpers.findField(clazz, fieldName).set(null, value);
			
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getCause());
			
		} catch (IllegalAccessException e) {
			throw new IllegalAccessError(e.getMessage());
		}
	}

	public static Object getParentObject(Object obj) throws NoSuchFieldError {
		return getField(obj, "this$0");
	}

	public static Set<XC_MethodHook.Unhook> hookConstructors(Class<?> hookClass, XC_MethodHook callback) {
		return XposedBridge.hookAllConstructors(hookClass, callback);
	}
	
	public static Set<XC_MethodHook.Unhook> hookConstructors(String className, XC_MethodHook callback) throws ClassNotFoundError {
		return XposedBridge.hookAllConstructors(findClass(className), callback);
	}
	
	public static Set<XC_MethodHook.Unhook> hookConstructors(String className, ClassLoader classLoader, XC_MethodHook callback) throws ClassNotFoundError {
		return XposedBridge.hookAllConstructors(findClass(className, classLoader), callback);
	}
	
	public static XC_MethodHook.Unhook hookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) throws NoSuchMethodError {
		XC_MethodHook callback = (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length-1];
		Object[] parameterTypes = new Object[parameterTypesAndCallback.length-1];
		
		for (int i=0; i < parameterTypesAndCallback.length-1; i++) {
			parameterTypes[i] = parameterTypesAndCallback[i];
		}
		
		return XposedBridge.hookMethod(findConstructor(clazz, parameterTypes), callback);
	}
	
	public static XC_MethodHook.Unhook hookConstructor(String className, ClassLoader classLoader, Object... parameterTypesAndCallback) throws NoSuchMethodError, ClassNotFoundError {
		return hookConstructor(findClass(className, classLoader), parameterTypesAndCallback);
	}
	
	public static XC_MethodHook.Unhook hookConstructor(String className, Object... parameterTypesAndCallback) throws NoSuchMethodError, ClassNotFoundError {
		return hookConstructor(findClass(className), parameterTypesAndCallback);
	}
	
	public static Set<XC_MethodHook.Unhook> hookMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
		return XposedBridge.hookAllMethods(hookClass, methodName, callback);
	}
	
	public static Set<XC_MethodHook.Unhook> hookMethods(String className, String methodName, XC_MethodHook callback) throws ClassNotFoundError {
		return XposedBridge.hookAllMethods(findClass(className), methodName, callback);
	}
	
	public static Set<XC_MethodHook.Unhook> hookMethods(String className, ClassLoader classLoader, String methodName, XC_MethodHook callback) throws ClassNotFoundError {
		return XposedBridge.hookAllMethods(findClass(className, classLoader), methodName, callback);
	}

	public static XC_MethodHook.Unhook hookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) throws NoSuchMethodError {
		XC_MethodHook callback = (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length-1];
		Object[] parameterTypes = new Object[parameterTypesAndCallback.length-1];
		
		for (int i=0; i < parameterTypesAndCallback.length-1; i++) {
			parameterTypes[i] = parameterTypesAndCallback[i];
		}
		
		return XposedBridge.hookMethod(findMethod(clazz, methodName, parameterTypes), callback);
	}
	
	public static XC_MethodHook.Unhook hookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) throws NoSuchMethodError, ClassNotFoundError {
		return hookMethod(findClass(className, classLoader), methodName, parameterTypesAndCallback);
	}
	
	public static XC_MethodHook.Unhook hookMethod(String className, String methodName, Object... parameterTypesAndCallback) throws NoSuchMethodError, ClassNotFoundError {
		return hookMethod(findClass(className), methodName, parameterTypesAndCallback);
	}
	
	public static Class<?> findClass(String className) throws ClassNotFoundError {
		return XposedHelpers.findClass(className, null);
	}
	
	public static Class<?> findClass(String className, ClassLoader classLoader) throws ClassNotFoundError {
		return XposedHelpers.findClass(className, classLoader);
	}
}
