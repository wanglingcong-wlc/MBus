package com.wlc.mbuslibs;


//每个类的订阅方法
public class SubscriberInfo extends AbstractSubscriberInfo {

  private final MethodInfo[] methodInfos;

  public SubscriberInfo(Class subscriberClass, boolean shouldCheckSuperclass, MethodInfo[] methodInfos) {
    super(subscriberClass, null, shouldCheckSuperclass);
    this.methodInfos = methodInfos;
  }

  @Override
  public synchronized SubscriberMethod[] getSubscriberMethods() {
    int length = methodInfos.length;
    SubscriberMethod[] methods = new SubscriberMethod[length];
    for (int i = 0; i < length; i++) {
      MethodInfo info = methodInfos[i];
      methods[i] = createSubscriberMethod(info.methodName, info.eventType, info.paramType, info.threadMode, info.sticky);
    }
    return methods;
  }
}
