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


@MRoute(path = "main")
public class MainActivity extends BaseActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);


    findViewById(R.id.text).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        //MRouteMain.get().build("secondactivity").navigation(MainActivity.this);
        //MBusMain.get().register(MainActivity.this);
        MBusMain.get().post("click", 999);
        MBusMain.get().post("login", null, new CallBack() {
          @Override
          public void onReturn(Object o) {

          }
        });
        MBusMain.get().postSticky(new EventT());

      }
    });
  }

  @MBus(type = "login", threadMode = ThreadMode.MAIN, isSticky = false)
  public void click(String i) {
    Log.e("qqqqqqqqqqqqqq", "receivenone" + i);
  }

  @MBus
  public int click(EventT i) {
    Log.e("qqqqqqqqqqqqqq", "receive" + i);
    return 3556;
  }

  @MBus
  public int click123(Integer i) {
    Log.e("qqqqqqqqqqqqqq", "receive null params");
    return 3556;
  }
}
