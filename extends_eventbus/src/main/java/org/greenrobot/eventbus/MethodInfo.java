package org.greenrobot.eventbus;


public class MethodInfo {//方法的表面数据，传入的数据
  final String methodName;
  final ThreadMode threadMode;
  final String eventType;
  final boolean sticky;
  final Class<?> paramType;

  public MethodInfo(String methodName, String eventType, Class<?> paramType, ThreadMode threadMode, boolean sticky) {
    this.methodName = methodName;
    this.threadMode = threadMode;
    this.eventType = eventType;
    this.sticky = sticky;
    this.paramType = paramType;
  }

  public MethodInfo(String methodName, String eventType, Class<?> paramType) {
    this(methodName, eventType, paramType, ThreadMode.POSTING, false);
  }

  public MethodInfo(String methodName, String eventType, Class<?> paramType, ThreadMode threadMode) {
    this(methodName, eventType, paramType, threadMode, false);
  }

}
