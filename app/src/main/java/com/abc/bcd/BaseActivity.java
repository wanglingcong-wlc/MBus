package com.abc.bcd;

import android.os.Bundle;

import com.wlc.mbuslibs.MBusMain;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Created on 2019-12-17.
 */
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
