/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
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
package com.wlc.mbuslibs;

import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class SubscriberMethodFinder {

  private static final int BRIDGE = 0x40;
  private static final int SYNTHETIC = 0x1000;

  private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
  private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();

  private List<SubscriberInfoIndex> subscriberInfoIndexes;
  private final boolean strictMethodVerification;
  private final boolean ignoreGeneratedIndex;

  private static final int POOL_SIZE = 4;
  private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];

  SubscriberMethodFinder(boolean strictMethodVerification,
                         boolean ignoreGeneratedIndex, List<Class<SubscriberInfoIndex>> classList) {
    this.strictMethodVerification = strictMethodVerification;
    this.ignoreGeneratedIndex = ignoreGeneratedIndex;
    initIndexes(classList);
  }

  void initIndexes(List<Class<SubscriberInfoIndex>> classList) {
    subscriberInfoIndexes = new ArrayList<>();
    try {
      for (Class<SubscriberInfoIndex> c : classList) {
        subscriberInfoIndexes.add(c.newInstance());
      }
    } catch (Exception e) {
      subscriberInfoIndexes.clear();
    }
  }

  List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
    List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
    if (subscriberMethods != null) {
      return subscriberMethods;
    }

    if (ignoreGeneratedIndex || subscriberInfoIndexes == null || subscriberInfoIndexes.isEmpty()) {
      subscriberMethods = findUsingReflection(subscriberClass);
    } else {
      subscriberMethods = findUsingInfo(subscriberClass);
    }
    if (subscriberMethods.isEmpty()) {
      throw new MBusException("Subscriber " + subscriberClass
          + " and its super classes have no public methods with the @MBus annotation");
    } else {
      METHOD_CACHE.put(subscriberClass, subscriberMethods);
      return subscriberMethods;
    }
  }

  private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
    FindState findState = prepareFindState();
    findState.initForSubscriber(subscriberClass);
    while (findState.clazz != null) {
      findState.subscriberInfo = getSubscriberInfo(findState);
      if (findState.subscriberInfo != null) {
        SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();//拿到一个类的所有订阅方法，可以反射的方法
        for (SubscriberMethod subscriberMethod : array) {
          if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
            findState.subscriberMethods.add(subscriberMethod);
          }
        }
      } else {
        findUsingReflectionInSingleClass(findState);
      }
      findState.moveToSuperclass();
    }
    return getMethodsAndRelease(findState);
  }

  private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {
    List<SubscriberMethod> subscriberMethods = new ArrayList<>(findState.subscriberMethods);
    findState.recycle();
    synchronized (FIND_STATE_POOL) {
      for (int i = 0; i < POOL_SIZE; i++) {
        if (FIND_STATE_POOL[i] == null) {
          FIND_STATE_POOL[i] = findState;
          break;
        }
      }
    }
    return subscriberMethods;
  }

  private FindState prepareFindState() {
    synchronized (FIND_STATE_POOL) {
      for (int i = 0; i < POOL_SIZE; i++) {
        FindState state = FIND_STATE_POOL[i];
        if (state != null) {
          FIND_STATE_POOL[i] = null;
          return state;
        }
      }
    }
    return new FindState();
  }

  private ISubscriberInfo getSubscriberInfo(FindState findState) {
    if (findState.subscriberInfo != null && findState.subscriberInfo.getSuperSubscriberInfo() != null) {
      SubscriberInfo superclassInfo = findState.subscriberInfo.getSuperSubscriberInfo();
      if (findState.clazz == superclassInfo.getSubscriberClass()) {
        return superclassInfo;
      }
    }
    if (subscriberInfoIndexes != null) {
      for (SubscriberInfoIndex index : subscriberInfoIndexes) {
        ISubscriberInfo info = index.getSubscriberInfo(findState.clazz);
        if (info != null) {
          return info;
        }
      }
    }
    return null;
  }

  private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
    FindState findState = prepareFindState();
    findState.initForSubscriber(subscriberClass);
    while (findState.clazz != null) {
      findUsingReflectionInSingleClass(findState);
      findState.moveToSuperclass();
    }
    return getMethodsAndRelease(findState);
  }

  private void findUsingReflectionInSingleClass(FindState findState) {
    Method[] methods;
    try {
      methods = findState.clazz.getDeclaredMethods();
    } catch (Throwable th) {
      methods = findState.clazz.getMethods();
      findState.skipSuperClasses = true;
    }
    for (Method method : methods) {
      int modifiers = method.getModifiers();
      if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length <= 1) {
          MBus subscribeAnnotation = method.getAnnotation(MBus.class);
          if (subscribeAnnotation != null) {
            if (TextUtils.isEmpty(subscribeAnnotation.type()) && (parameterTypes == null || parameterTypes.length == 0)){
              throw new MBusException("type and param can not be null both");
            }
            if (findState.checkAdd(method, subscribeAnnotation.type())) {
              ThreadMode threadMode = subscribeAnnotation.threadMode();

              findState.subscriberMethods.add(new SubscriberMethod(
                  method,
                  TextUtils.isEmpty(subscribeAnnotation.type()) ? parameterTypes[0].getName() : subscribeAnnotation.type(),
                  (parameterTypes == null || parameterTypes.length == 0) ? null : parameterTypes[0],
                  threadMode,
                  subscribeAnnotation.isSticky()
              ));
            }
          }
        } else if (strictMethodVerification && method.isAnnotationPresent(MBus.class)) {
          String methodName = method.getDeclaringClass().getName() + "." + method.getName();
          throw new MBusException("@MBus method " + methodName +
              "must have less than 1 parameter but has " + parameterTypes.length);
        }
      } else if (strictMethodVerification && method.isAnnotationPresent(MBus.class)) {
        String methodName = method.getDeclaringClass().getName() + "." + method.getName();
        throw new MBusException(methodName +
            " is a illegal @MBus method: must be public, non-static, and non-abstract");
      }
    }
  }

  static void clearCaches() {
    METHOD_CACHE.clear();
  }

  static class FindState {
    final List<SubscriberMethod> subscriberMethods = new ArrayList<>();
    final Map<String, Object> anyMethodByEventType = new HashMap<>();
    final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();
    final StringBuilder methodKeyBuilder = new StringBuilder(128);

    Class<?> subscriberClass;
    Class<?> clazz;
    boolean skipSuperClasses;
    ISubscriberInfo subscriberInfo;

    void initForSubscriber(Class<?> subscriberClass) {
      this.subscriberClass = clazz = subscriberClass;
      skipSuperClasses = false;
      subscriberInfo = null;
    }

    void recycle() {
      subscriberMethods.clear();
      anyMethodByEventType.clear();
      subscriberClassByMethodKey.clear();
      methodKeyBuilder.setLength(0);
      subscriberClass = null;
      clazz = null;
      skipSuperClasses = false;
      subscriberInfo = null;
    }

    boolean checkAdd(Method method, String eventType) {
      Object existing = anyMethodByEventType.put(eventType, method);
      if (existing == null) {
        return true;
      } else {
        if (existing instanceof Method) {
          if (!checkAddWithMethodSignature((Method) existing, eventType)) {
            throw new IllegalStateException();
          }
          anyMethodByEventType.put(eventType, this);
        }
        return checkAddWithMethodSignature(method, eventType);
      }
    }

    private boolean checkAddWithMethodSignature(Method method, String eventType) {
      methodKeyBuilder.setLength(0);
      methodKeyBuilder.append(method.getName());
      methodKeyBuilder.append('>').append(eventType);

      String methodKey = methodKeyBuilder.toString();
      Class<?> methodClass = method.getDeclaringClass();
      Class<?> methodClassOld = subscriberClassByMethodKey.put(methodKey, methodClass);
      if (methodClassOld == null || methodClassOld.isAssignableFrom(methodClass)) {
        return true;
      } else {
        subscriberClassByMethodKey.put(methodKey, methodClassOld);
        return false;
      }
    }

    void moveToSuperclass() {
      if (skipSuperClasses) {
        clazz = null;
      } else {
        clazz = clazz.getSuperclass();
        String clazzName = clazz.getName();
        if (clazzName.startsWith("java.") || clazzName.startsWith("javax.") || clazzName.startsWith("android.") || clazzName.startsWith("androidx.")) {
          clazz = null;
        }
      }
    }
  }

}
