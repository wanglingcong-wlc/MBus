package com.abc.bcd;

import android.app.Application;

import org.greenrobot.eventbus.EventBus;

public class MyApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    EventBus.builder().build(this);
  }
}
