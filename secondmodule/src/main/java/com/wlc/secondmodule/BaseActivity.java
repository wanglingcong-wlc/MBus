package com.wlc.secondmodule;

import android.os.Bundle;

import com.wlc.mbuslibs.MBusMain;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;


public class BaseActivity extends AppCompatActivity {
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    MBusMain.get().register(this);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    MBusMain.get().unregister(this);
  }
}
