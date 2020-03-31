package org.greenrobot.eventbus;


public interface ISubscriberInfo {
  Class<?> getSubscriberClass();

  SubscriberMethod[] getSubscriberMethods();

  SubscriberInfo getSuperSubscriberInfo();

  boolean shouldCheckSuperclass();
}
