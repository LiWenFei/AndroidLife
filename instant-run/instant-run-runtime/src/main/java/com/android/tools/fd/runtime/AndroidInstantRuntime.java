/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.fd.runtime;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.fd.common.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.android.tools.fd.common.Log.logging;

/**
 * Generic Instant Run services. must not depend on Android APIs.
 *
 * TODO: transform this static methods into interface/implementation.
 */
@SuppressWarnings("unused")
public class AndroidInstantRuntime {

    protected interface Logging {
        void log(@NonNull Level level, @NonNull String string);

        boolean isLoggable(@NonNull Level level);

        void log(@NonNull Level level, @NonNull String string, @Nullable Throwable throwable);
    }


    public static void setLogger(final Logger logger) {

        logging = new Log.Logging() {
            @Override
            public void log(@NonNull Level level, @NonNull String string) {
                logger.log(level, string);
            }


            @Override
            public boolean isLoggable(@NonNull Level level) {
                return logger.isLoggable(level);
            }


            @Override
            public void log(@NonNull Level level, @NonNull String string,
                            @Nullable Throwable throwable) {
                logger.log(level, string, throwable);
            }
        };
    }


    /**
     * 反射获取 Field 的值
     *
     * @param targetClass 目标类 Class
     * @param fieldName Field name
     * @return 目标 Field 的值
     */
    @Nullable
    public static Object getStaticPrivateField(Class targetClass, String fieldName) {
        return getPrivateField(null /* targetObject */, targetClass, fieldName);
    }


    /**
     * Hook 私有 Field 的值
     *
     * @param value 要设置的 Field 值
     * @param targetClass 目标类 Class
     * @param fieldName Field name
     */
    public static void setStaticPrivateField(
        @NonNull Object value, @NonNull Class targetClass, @NonNull String fieldName) {
        setPrivateField(null /* targetObject */, value, targetClass, fieldName);
    }


    /**
     * Hook 私有 Field 的值
     *
     * @param targetObject 目标类 实例
     * @param value 要设置的 Field 值
     * @param targetClass 目标类 Class
     * @param fieldName Field name
     */
    public static void setPrivateField(
        @Nullable Object targetObject,
        @Nullable Object value,
        @NonNull Class targetClass,
        @NonNull String fieldName) {

        try {
            Field declaredField = getField(targetClass, fieldName);
            declaredField.set(targetObject, value);
        } catch (IllegalAccessException e) {
            if (logging != null) {
                logging.log(Level.SEVERE,
                    String.format("Exception during setPrivateField %s", fieldName), e);
            }
            throw new RuntimeException(e);
        }
    }


    /**
     * 反射获取 私有 Field 的值
     *
     * @param targetObject 目标类 实例
     * @param targetClass 目标类 Class
     * @param fieldName Field name
     * @return 私有 Field 的值
     */
    @Nullable
    public static Object getPrivateField(
        @Nullable Object targetObject,
        @NonNull Class targetClass,
        @NonNull String fieldName) {

        try {
            Field declaredField = getField(targetClass, fieldName);
            return declaredField.get(targetObject);
        } catch (IllegalAccessException e) {
            if (logging != null) {
                logging.log(Level.SEVERE,
                    String.format("Exception during%1$s getField %2$s",
                        targetObject == null ? " static" : "",
                        fieldName), e);
            }
            throw new RuntimeException(e);
        }
    }


    /**
     * 反射获取 Field，并且设置为 public
     *
     * @param target 目标类 Class
     * @param name Field name
     * @return Field
     */
    @NonNull
    private static Field getField(Class target, String name) {
        Field declareField = getFieldByName(target, name);
        if (declareField == null) {
            throw new RuntimeException(new NoSuchElementException(name));
        }
        declareField.setAccessible(true);
        return declareField;
    }


    /**
     * 反射调用 目标类 的方法
     *
     * @param receiver 目标类 实例
     * @param params 参数值 Object[]
     * @param parameterTypes 方法参数类型 Class[]
     * @param methodName 方法名
     * @return 方法返回值
     * @throws Throwable
     */
    public static Object invokeProtectedMethod(Object receiver,
                                               Object[] params,
                                               Class[] parameterTypes,
                                               String methodName) throws Throwable {

        if (logging != null && logging.isLoggable(Level.FINE)) {
            logging.log(Level.FINE,
                String.format("protectedMethod:%s on %s", methodName, receiver));
        }
        try {
            Method toDispatchTo = getMethodByName(receiver.getClass(), methodName, parameterTypes);
            if (toDispatchTo == null) {
                throw new RuntimeException(new NoSuchMethodException(methodName));
            }
            toDispatchTo.setAccessible(true);
            return toDispatchTo.invoke(receiver, params);
        } catch (InvocationTargetException e) {
            // The called method threw an exception, rethrow
            throw e.getCause();
        } catch (IllegalAccessException e) {
            logging.log(Level.SEVERE, String.format("Exception while invoking %s", methodName), e);
            throw new RuntimeException(e);
        }
    }


    /**
     * 反射调用 目标类 的方法
     *
     * @param params 参数值 Object[]
     * @param parameterTypes 方法参数类型 Class[]
     * @param methodName 方法名
     * @param receiverClass 目标类 Class
     * @return 方法返回值
     * @throws Throwable
     */
    public static Object invokeProtectedStaticMethod(
        Object[] params,
        Class[] parameterTypes,
        String methodName,
        Class receiverClass) throws Throwable {

        if (logging != null && logging.isLoggable(Level.FINE)) {
            logging.log(Level.FINE,
                String.format("protectedStaticMethod:%s on %s", methodName,
                    receiverClass.getName()));
        }
        try {
            Method toDispatchTo = getMethodByName(receiverClass, methodName, parameterTypes);
            if (toDispatchTo == null) {
                throw new RuntimeException(new NoSuchMethodException(
                    methodName + " in class " + receiverClass.getName()));
            }
            toDispatchTo.setAccessible(true);
            return toDispatchTo.invoke(null /* target */, params);
        } catch (InvocationTargetException e) {
            // The called method threw an exception, rethrow
            throw e.getCause();
        } catch (IllegalAccessException e) {
            logging.log(Level.SEVERE, String.format("Exception while invoking %s", methodName), e);
            throw new RuntimeException(e);
        }
    }


    /**
     * 反射实例化目标类
     *
     * @param params 构造方法参数值 Object[]
     * @param paramTypes 构造方法参数类型 Class[]
     * @param targetClass 目标类 Class
     * @param <T> Class 类型
     * @return 目标类实例
     * @throws Throwable
     */
    public static <T> T newForClass(Object[] params, Class[] paramTypes, Class<T> targetClass)
        throws Throwable {
        Constructor declaredConstructor;
        try {
            declaredConstructor = targetClass.getDeclaredConstructor(paramTypes);
        } catch (NoSuchMethodException e) {
            logging.log(Level.SEVERE, "Exception while resolving constructor", e);
            throw new RuntimeException(e);
        }
        declaredConstructor.setAccessible(true);
        try {
            return targetClass.cast(declaredConstructor.newInstance(params));
        } catch (InvocationTargetException e) {
            // The called method threw an exception, rethrow
            throw e.getCause();
        } catch (InstantiationException e) {
            logging.log(Level.SEVERE,
                String.format("Exception while instantiating %s", targetClass), e);
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            logging.log(Level.SEVERE,
                String.format("Exception while instantiating %s", targetClass), e);
            throw new RuntimeException(e);
        }
    }


    /**
     * 反射获取 Field
     *
     * @param aClass 目标类 Class
     * @param name Field name
     * @return field
     */
    private static Field getFieldByName(Class<?> aClass, String name) {

        if (logging != null && logging.isLoggable(Level.FINE)) {
            logging.log(Level.FINE,
                String.format("getFieldByName:%s in %s", name, aClass.getName()));
        }

        Class<?> currentClass = aClass;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                // ignored.
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }


    /**
     * 反射获取 Method
     *
     * @param aClass 目标类 Class
     * @param name Method name
     * @param paramTypes 方法参数
     * @return Method
     */
    private static Method getMethodByName(Class<?> aClass, String name, Class[] paramTypes) {

        if (aClass == null) {
            return null;
        }

        Class<?> currentClass = aClass;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                // ignored.
            }
            currentClass = currentClass.getSuperclass();
            if (currentClass != null && logging != null && logging.isLoggable(Level.FINE)) {
                logging.log(Level.FINE, String.format(
                    "getMethodByName:Looking in %s now", currentClass.getName()));
            }

        }
        return null;
    }


    public static void trace(String s) {
        if (logging != null) {
            logging.log(Level.FINE, s);
        }
    }


    public static void trace(String s1, String s2) {
        if (logging != null) {
            logging.log(Level.FINE, String.format("%s %s", s1, s2));
        }
    }


    public static void trace(String s1, String s2, String s3) {
        if (logging != null) {
            logging.log(Level.FINE, String.format("%s %s %s", s1, s2, s3));
        }
    }


    public static void trace(String s1, String s2, String s3, String s4) {
        if (logging != null) {
            logging.log(Level.FINE, String.format("%s %s %s %s", s1, s2, s3, s4));
        }
    }

}
