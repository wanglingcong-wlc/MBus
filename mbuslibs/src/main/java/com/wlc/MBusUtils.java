package com.wlc;


import android.content.Context;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

public class MBusUtils {
  public static <T> List<Class<T>> scanDex(Context ctx, String entityPackage) {
    List<Class<T>> cl = new ArrayList<>();
    try {
      PathClassLoader classLoader = (PathClassLoader) Thread
          .currentThread().getContextClassLoader();

      DexFile dex = new DexFile(ctx.getPackageResourcePath());
      Enumeration<String> entries = dex.entries();
      while (entries.hasMoreElements()) {
        String entryName = entries.nextElement();
        if (entryName.contains(entityPackage)) {
          Class<T> entryClass = (Class<T>) Class.forName(entryName, true, classLoader);
          cl.add(entryClass);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return cl;
  }
}
