package com.wlc.mbuslibs;


public interface ISubscriberInfo {
  Class<?> getSubscriberClass();

  SubscriberMethod[] getSubscriberMethods();

  SubscriberInfo getSuperSubscriberInfo();

  boolean shouldCheckSuperclass();
}
