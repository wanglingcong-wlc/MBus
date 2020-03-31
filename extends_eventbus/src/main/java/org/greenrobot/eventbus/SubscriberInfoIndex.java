package org.greenrobot.eventbus;

public interface SubscriberInfoIndex {
  ISubscriberInfo getSubscriberInfo(Class<?> subscriberClass);
}
