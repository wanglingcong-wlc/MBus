package com.wlc.mbuslibs;

import java.lang.reflect.Method;

public class SubscriberMethod {  //执行时需要的方法数据
  final Method method;
  final ThreadMode threadMode;
  final String eventType;
  final Class<?> paramType;
  final boolean sticky;
  /**
   * Used for efficient comparison
   */
  String methodString;

  public SubscriberMethod(Method method, String eventType, Class<?> paramType, ThreadMode threadMode, boolean sticky) {
    this.method = method;
    this.threadMode = threadMode;
    this.eventType = eventType;
    this.paramType = paramType;
    this.sticky = sticky;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (other instanceof SubscriberMethod) {
      checkMethodString();
      SubscriberMethod otherSubscriberMethod = (SubscriberMethod) other;
      otherSubscriberMethod.checkMethodString();
      return methodString.equals(otherSubscriberMethod.methodString);
    } else {
      return false;
    }
  }

  private synchronized void checkMethodString() {
    if (methodString == null) {
      StringBuilder builder = new StringBuilder(64);
      builder.append(method.getDeclaringClass().getName());
      builder.append('#').append(method.getName());
      builder.append('(').append(eventType);
      builder.append(')').append(paramType.getName());
      methodString = builder.toString();
    }
  }

  @Override
  public int hashCode() {
    return method.hashCode();
  }
}
