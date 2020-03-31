/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.greenrobot.eventbus;

import android.content.Context;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class EventBusBuilder {
  private final static ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();

  boolean logSubscriberExceptions = true;
  boolean logNoSubscriberMessages = true;
  boolean sendSubscriberExceptionEvent = true;
  boolean sendNoSubscriberEvent = true;
  boolean throwSubscriberException;
  boolean ignoreGeneratedIndex;
  boolean strictMethodVerification;
  ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;
  List<Class<?>> skipMethodVerificationForClasses;
  List<SubscriberInfoIndex> subscriberInfoIndexes;
  Logger logger;
  MainThreadSupport mainThreadSupport;

  EventBusBuilder() {
  }

  /** Default: true */
  public EventBusBuilder logSubscriberExceptions(boolean logSubscriberExceptions) {
    this.logSubscriberExceptions = logSubscriberExceptions;
    return this;
  }

  /** Default: true */
  public EventBusBuilder logNoSubscriberMessages(boolean logNoSubscriberMessages) {
    this.logNoSubscriberMessages = logNoSubscriberMessages;
    return this;
  }

  /** Default: true */
  public EventBusBuilder sendSubscriberExceptionEvent(boolean sendSubscriberExceptionEvent) {
    this.sendSubscriberExceptionEvent = sendSubscriberExceptionEvent;
    return this;
  }

  /** Default: true */
  public EventBusBuilder sendNoSubscriberEvent(boolean sendNoSubscriberEvent) {
    this.sendNoSubscriberEvent = sendNoSubscriberEvent;
    return this;
  }


  public EventBusBuilder throwSubscriberException(boolean throwSubscriberException) {
    this.throwSubscriberException = throwSubscriberException;
    return this;
  }


  public EventBusBuilder executorService(ExecutorService executorService) {
    this.executorService = executorService;
    return this;
  }


  public EventBusBuilder skipMethodVerificationFor(Class<?> clazz) {
    if (skipMethodVerificationForClasses == null) {
      skipMethodVerificationForClasses = new ArrayList<>();
    }
    skipMethodVerificationForClasses.add(clazz);
    return this;
  }

  public EventBusBuilder ignoreGeneratedIndex(boolean ignoreGeneratedIndex) {
    this.ignoreGeneratedIndex = ignoreGeneratedIndex;
    return this;
  }

  public EventBusBuilder strictMethodVerification(boolean strictMethodVerification) {
    this.strictMethodVerification = strictMethodVerification;
    return this;
  }

  public EventBusBuilder addIndex(SubscriberInfoIndex index) {
    if (subscriberInfoIndexes == null) {
      subscriberInfoIndexes = new ArrayList<>();
    }
    subscriberInfoIndexes.add(index);
    return this;
  }


  public EventBusBuilder logger(Logger logger) {
    this.logger = logger;
    return this;
  }

  Logger getLogger() {
    if (logger != null) {
      return logger;
    } else {
      return Logger.AndroidLogger.isAndroidLogAvailable() && getAndroidMainLooperOrNull() != null
          ? new Logger.AndroidLogger("EventBus") :
          new Logger.SystemOutLogger();
    }
  }


  MainThreadSupport getMainThreadSupport() {
    if (mainThreadSupport != null) {
      return mainThreadSupport;
    } else if (Logger.AndroidLogger.isAndroidLogAvailable()) {
      Object looperOrNull = getAndroidMainLooperOrNull();
      return looperOrNull == null ? null :
          new MainThreadSupport.AndroidHandlerMainThreadSupport((Looper) looperOrNull);
    } else {
      return null;
    }
  }

  Object getAndroidMainLooperOrNull() {
    try {
      return Looper.getMainLooper();
    } catch (RuntimeException e) {
      return null;
    }
  }

  public EventBus build(Context context) {
    if (context == null) {
      throw new EventBusException("mbus init params is null!!!");
    }

    synchronized (EventBus.class) {
      if (EventBus.defaultInstance != null) {
        throw new EventBusException("Default instance already exists." +
            " It may be only set once before it's used the first time to ensure consistent behavior.");
      }
      EventBus.defaultInstance = new EventBus(this, context.getApplicationContext());
      return EventBus.defaultInstance;
    }
  }

}
