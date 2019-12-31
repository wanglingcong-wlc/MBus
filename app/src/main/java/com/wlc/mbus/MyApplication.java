package com.wlc.mbus;

import android.app.Application;

import com.wlc.mbuslibs.MBusMain;
import com.wlc.mroute.MRouteMain;

public class MyApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    MBusMain.builder().addIndex(new MBusIndex()).addIndex(new com.wlc.mbus.secondmodule.MBusIndex()).installDefaultMBus();
    MRouteMain.getInstance().init(this);
  }
}
