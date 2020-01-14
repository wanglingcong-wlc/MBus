package com.abc.bcd;


import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.abc.EventT;
import com.wlc.mbuslibs.CallBack;
import com.wlc.mbuslibs.MBus;
import com.wlc.mbuslibs.MBusMain;
import com.wlc.mbuslibs.ThreadMode;
import com.wlc.mroute.MRoute;
import com.wlc.mroute.MRouteMain;


@MRoute(path = "main")
public class MainActivity extends BaseActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);


    findViewById(R.id.text).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        MRouteMain.get().build("secondactivity").navigation(MainActivity.this);
        //MBusMain.get().register(MainActivity.this);
//        MBusMain.get().post("test");
//        MBusMain.get().post(new Boolean(false));
//        MBusMain.get().post("test2",333);

      }
    });
  }

  @MBus(type = "test")
  public void testmbus(){
    Log.e("qqqqqqqqqqq","receive test");
  }

  @MBus
  public void testmbus2(Boolean obj){
    Log.e("qqqqqqqqqqq","receive string");
  }

  @MBus(type = "test2",threadMode = ThreadMode.THREADPOOL)
  public void testmbus3(Integer i){
    Log.e("qqqqqqqqqqq","receive int");
  }
}
