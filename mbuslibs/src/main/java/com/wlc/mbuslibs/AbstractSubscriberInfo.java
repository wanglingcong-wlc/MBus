package com.wlc.mbuslibs;

import java.lang.reflect.Method;

public abstract class AbstractSubscriberInfo implements ISubscriberInfo {
  private final Class subscriberClass;
  private final Class<? extends SubscriberInfo> superSubscriberInfoClass;
  private final boolean shouldCheckSuperclass;

  protected AbstractSubscriberInfo(Class subscriberClass, Class<? extends SubscriberInfo> superSubscriberInfoClass,
                                   boolean shouldCheckSuperclass) {
    this.subscriberClass = subscriberClass;
    this.superSubscriberInfoClass = superSubscriberInfoClass;
    this.shouldCheckSuperclass = shouldCheckSuperclass;
  }

  @Override
  public Class getSubscriberClass() {
    return subscriberClass;
  }

  @Override
  public SubscriberInfo getSuperSubscriberInfo() {
    if (superSubscriberInfoClass == null) {
      return null;
    }
    try {
      return superSubscriberInfoClass.newInstance();
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean shouldCheckSuperclass() {
    return shouldCheckSuperclass;
  }

  protected SubscriberMethod createSubscriberMethod(String methodName, String eventType, Class<?> paramType) {
    return createSubscriberMethod(methodName, eventType, paramType, ThreadMode.THREADNOW, false);
  }

  protected SubscriberMethod createSubscriberMethod(String methodName, String eventType, Class<?> paramType, ThreadMode threadMode) {
    return createSubscriberMethod(methodName, eventType, paramType, threadMode, false);
  }

  protected SubscriberMethod createSubscriberMethod(String methodName, String eventType, Class<?> paramType, ThreadMode threadMode, boolean sticky) {
    try {
      Method method;
      if (paramType == null) {
        method = subscriberClass.getDeclaredMethod(methodName);
      } else {
        method = subscriberClass.getDeclaredMethod(methodName, paramType);
      }
      return new SubscriberMethod(method, eventType, paramType, threadMode, sticky);
    } catch (NoSuchMethodException e) {
      throw new MBusException("Could not find subscriber (" + methodName + " with params " + paramType + ") in " + subscriberClass.toString() +
          ". Maybe a missing ProGuard rule?", e);
    }
  }

}

