package com.abc.bcd;


import android.app.usage.UsageEvents;
import android.os.Bundle;
import android.util.Log;
import android.view.View;


import com.abc.EventT;

import org.greenrobot.eventbus.CallBack;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;


public class MainActivity extends BaseActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);


    findViewById(R.id.text).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        //MRouteMain.get().build("secondactivity").navigation(MainActivity.this);
        //EventBus.getDefault().register(MainActivity.this);
        EventBus.getDefault().post("test");
        EventBus.getDefault().post(new Boolean(false));
        EventBus.getDefault().post("test2", 333, new CallBack() {
          @Override
          public void onReturn(Object o) {

          }
        });
        EventBus.getDefault().post(new EventT());
      }
    });
  }

  @Subscribe
  public void test_object(EventT eventT){
    Log.e("qqqqqqqqqqq","receive test_object");
  }

  @Subscribe(type = "test")
  public void testmbus(){
    Log.e("qqqqqqqqqqq","receive test");
  }

  @Subscribe
  public void testmbus2(Boolean obj){
    Log.e("qqqqqqqqqqq","receive string");
  }

  @Subscribe(type = "test2",threadMode = ThreadMode.MAIN)
  public void testmbus3(Integer i){
    Log.e("qqqqqqqqqqq","receive int");
  }
}
