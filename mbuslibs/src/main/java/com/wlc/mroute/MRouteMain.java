package com.wlc.mroute;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import androidx.core.app.ActivityCompat;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

import static com.wlc.Constants.MROUTE_INDEX_HEAD;

public class MRouteMain {
  private static MRouteMain instance = null;
  private boolean hasInit;

  public static final MRouteMain getInstance() {
    if (instance == null) {
      synchronized (MRouteMain.class) {
        if (instance == null) {
          instance = new MRouteMain();
        }
      }
    }
    return instance;
  }

  private Application mApplication;

  private Map<String, Class<?>> mRouteIndex = new HashMap<>();
  private static Handler mHandler;

  public void init(Application application) {
    if (hasInit) {
      throw new MRouteException("mroute has inited");
    }
    hasInit = true;
    this.mApplication = application;
    mHandler = new Handler(Looper.getMainLooper());
    scan(application, MROUTE_INDEX_HEAD);
  }

  public void navigation(Context context, final MRouteInfo routeInfo, final int requestCode) {
    final Context currentContext = null == context ? mApplication : context;
    final Intent intent = new Intent(currentContext, routeInfo.getPathClass());
    intent.putExtras(routeInfo.getExtras());

    int flags = routeInfo.getFlags();
    if (-1 != flags) {
      intent.setFlags(flags);
    } else if (!(currentContext instanceof Activity)) {    // Non activity, need less one flag.
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    runInMainThread(new Runnable() {
      @Override
      public void run() {
        startActivity(requestCode, currentContext, intent, routeInfo);
      }
    });
  }


  private void runInMainThread(Runnable runnable) {
    if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
      mHandler.post(runnable);
    } else {
      runnable.run();
    }
  }

  public void scan(Context ctx, String entityPackage) {
    try {
      PathClassLoader classLoader = (PathClassLoader) Thread
          .currentThread().getContextClassLoader();

      DexFile dex = new DexFile(ctx.getPackageResourcePath());
      Enumeration<String> entries = dex.entries();
      while (entries.hasMoreElements()) {
        String entryName = entries.nextElement();
        if (entryName.contains(entityPackage)) {
          Class<IRouteIndex> entryClass = (Class<IRouteIndex>) Class.forName(entryName, true, classLoader);
          initRouteIndex(entryClass);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void initRouteIndex(Class<IRouteIndex> indexClass) throws Exception {
    IRouteIndex index = indexClass.newInstance();
    index.getRouteInfos(mRouteIndex);
  }

  private void startActivity(int requestCode, Context currentContext, Intent intent, MRouteInfo routeInfo) {
    if (requestCode >= 0) {  // Need start for result
      if (currentContext instanceof Activity) {
        ActivityCompat.startActivityForResult((Activity) currentContext, intent, requestCode, null);
      } else {
        //log something
      }
    } else {
      ActivityCompat.startActivity(currentContext, intent, null);
    }

    if ((-1 != routeInfo.getEnterAnim() && -1 != routeInfo.getExitAnim()) && currentContext instanceof Activity) {    // Old version.
      ((Activity) currentContext).overridePendingTransition(routeInfo.getEnterAnim(), routeInfo.getExitAnim());
    }
  }


  public MRouteInfo build(String path) {
    Class<?> pathclass = mRouteIndex.get(path);

    if (TextUtils.isEmpty(path) || pathclass == null) {
      throw new MRouteException("path is empty or there is no class for path " + path);
    } else {

      return new MRouteInfo(path, mRouteIndex.get(path));
    }
  }
}
