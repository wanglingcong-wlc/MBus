package com.bcd.secondmodule;


import android.os.Bundle;
import android.view.View;

import com.wlc.secondmodule.R;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

public class SecondActivity extends BaseActivity {


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_second);

    findViewById(R.id.text).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        //MBusMain.getDefault().register(SecondActivity.this);
        EventBus.getDefault().post("click", 999);
        //MRouteMain.get().build("main").withString("username","wlc").navigation(SecondActivity.this,1001);

      }
    });

  }

  @Subscribe(type = "")
  public void click(String i){
  }

  @Subscribe(type = "click")
  public int click(Integer i){

    return 3556;
  }


}
