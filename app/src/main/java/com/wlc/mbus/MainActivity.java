package com.wlc.mbus;


import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.wlc.mbuslibs.MBus;
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
        MRouteMain.getInstance().build("secondactivity").navigation(MainActivity.this);
        //MBusMain.getDefault().register(MainActivity.this);
        //MBusMain.getDefault().post("click", 999);
      }
    });
  }

  @MBus(type = "")
  public void click(String i) {
  }

  @MBus(type = "click")
  public int click(Integer i) {
    return 3556;
  }


}
