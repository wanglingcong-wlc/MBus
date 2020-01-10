package com.bcd.secondmodule;


import android.os.Bundle;
import android.view.View;

import com.wlc.mbuslibs.MBus;
import com.wlc.mroute.MRoute;
import com.wlc.mroute.MRouteMain;
import com.wlc.secondmodule.R;

@MRoute(path = "secondactivity")
public class SecondActivity extends BaseActivity {


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_second);

    findViewById(R.id.text).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        //MBusMain.getDefault().register(SecondActivity.this);
        //MBusMain.getDefault().post("click", 999);
        MRouteMain.get().build("main").withString("username","wlc").navigation(SecondActivity.this,1001);

      }
    });

  }

  @MBus(type = "")
  public void click(String i){
  }

  @MBus(type = "click")
  public int click(Integer i){

    return 3556;
  }


}
