package com.abc.bcd;

import android.app.Application;

import com.wlc.mbuslibs.MBusMain;
import com.wlc.mroute.MRouteMain;

public class MyApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    MBusMain.builder().build(this);
    MRouteMain.get().init(this);
  }
}
