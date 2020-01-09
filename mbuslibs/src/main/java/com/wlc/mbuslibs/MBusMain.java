package com.wlc.mbuslibs;

import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;


public class MBusMain {

  public static final String NOEVENT = "noevent";

  /**
   * Log tag, apps may override it.
   */
  public static String TAG = "MBusMain";

  static volatile MBusMain defaultInstance;

  private static final MBusBuilder DEFAULT_BUILDER = new MBusBuilder();
  private static final Map<String, List<Class<?>>> eventTypesCache = new HashMap<>();

  private final Map<String, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
  private final Map<Object, List<String>> typesBySubscriber;
  private final Map<String, StickySingle> stickyEvents;

  private final ThreadLocal<PostingThreadState> currentPostingThreadState = new ThreadLocal<PostingThreadState>() {
    @Override
    protected PostingThreadState initialValue() {
      return new PostingThreadState();
    }
  };

  // @Nullable
  private final MainThreadSupport mainThreadSupport;
  // @Nullable
  private final Poster mainThreadPoster;
  private final BackgroundPoster backgroundPoster;
  private final AsyncPoster asyncPoster;
  private final SubscriberMethodFinder subscriberMethodFinder;
  private final ExecutorService executorService;

  private final boolean logNoSubscriberMessages;
  private final boolean sendNoSubscriberEvent;

  private final int indexCount;
  private final Logger logger;

  public static MBusMain get() {
    if (defaultInstance == null) {
      throw new MBusException("mbus has not build, please call MBusMain.builder().build(context)");
    }
    return defaultInstance;
  }

  public static MBusBuilder builder() {
    return new MBusBuilder();
  }


  public static void clearCaches() {
    SubscriberMethodFinder.clearCaches();
    eventTypesCache.clear();
  }


  private MBusMain() {
    this(DEFAULT_BUILDER, null);
  }

  MBusMain(MBusBuilder builder, Context context) {
    logger = builder.getLogger();
    subscriptionsByEventType = new HashMap<>();
    typesBySubscriber = new HashMap<>();
    stickyEvents = new ConcurrentHashMap<>();
    mainThreadSupport = builder.getMainThreadSupport();
    mainThreadPoster = mainThreadSupport != null ? mainThreadSupport.createPoster(this) : null;
    backgroundPoster = new BackgroundPoster(this);
    asyncPoster = new AsyncPoster(this);
    indexCount = builder.subscriberInfoIndexes != null ? builder.subscriberInfoIndexes.size() : 0;
    subscriberMethodFinder = new SubscriberMethodFinder(context,
        builder.strictMethodVerification, builder.ignoreGeneratedIndex);
    logNoSubscriberMessages = builder.logNoSubscriberMessages;
    sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
    executorService = builder.executorService;
  }

  public void register(Object subscriber) {
    Class<?> subscriberClass = subscriber.getClass();
    List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
    synchronized (this) {
      for (SubscriberMethod subscriberMethod : subscriberMethods) {
        subscribe(subscriber, subscriberMethod);
      }
    }
  }

  private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
    String eventType = subscriberMethod.eventType;
    Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
    CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);//该类型的所有方法
    if (subscriptions == null) {
      subscriptions = new CopyOnWriteArrayList<>();
      subscriptionsByEventType.put(eventType, subscriptions);
    } else {
      if (subscriptions.contains(newSubscription)) {
        throw new MBusException("Subscriber " + subscriber.getClass() + " already registered to event "
            + eventType);
      }
    }

    subscriptions.add(newSubscription);

    List<String> subscribedEvents = typesBySubscriber.get(subscriber);
    if (subscribedEvents == null) {
      subscribedEvents = new ArrayList<>();
      typesBySubscriber.put(subscriber, subscribedEvents);
    }
    subscribedEvents.add(eventType);

    if (subscriberMethod.sticky) {
      StickySingle stickyEvent = stickyEvents.get(eventType);
      newSubscription.callBack = stickyEvent.callBack;
      checkPostStickyEventToSubscription(newSubscription, stickyEvent);
    }
  }

  /**
   * @param newSubscription 标记为粘性事件的方法
   * @param stickyEvent     方法参数
   */
  private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
    if (stickyEvent != null) {
      postToSubscription(newSubscription, stickyEvent, isMainThread());
    }
  }

  private boolean isMainThread() {
    return mainThreadSupport != null ? mainThreadSupport.isMainThread() : true;
  }

  public synchronized boolean isRegistered(Object subscriber) {
    return typesBySubscriber.containsKey(subscriber);
  }

  private void unsubscribeByEventType(Object subscriber, String eventType) {
    List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
    if (subscriptions != null) {
      int size = subscriptions.size();
      for (int i = 0; i < size; i++) {
        Subscription subscription = subscriptions.get(i);
        if (subscription.subscriber == subscriber) {
          subscription.active = false;
          subscriptions.remove(i);
          i--;
          size--;
        }
      }
    }
  }


  public synchronized void unregister(Object subscriber) {
    List<String> subscribedTypes = typesBySubscriber.get(subscriber);
    if (subscribedTypes != null) {
      for (String eventType : subscribedTypes) {
        unsubscribeByEventType(subscriber, eventType);
      }
      typesBySubscriber.remove(subscriber);
    } else {
      logger.log(Level.WARNING, "Subscriber to unregister was not registered before: " + subscriber.getClass());
    }
  }

  /**
   * @param tag 发送的类型名称
   * @param obj 参数
   */
  public void post(String tag, Object obj) {
    post(tag, obj, null);
  }

  /**
   * @param tag 发送的类型名称
   * @param obj 参数
   * @param callBack 返回值回调
   */
  public void post(String tag, Object obj, CallBack callBack) {
    PostingThreadState postingState = currentPostingThreadState.get();
    List<QueueSingle> eventQueue = postingState.eventQueue;
    eventQueue.add(new QueueSingle(tag, obj, callBack));

    if (!postingState.isPosting) {
      postingState.isMainThread = isMainThread();
      postingState.isPosting = true;
      if (postingState.canceled) {
        throw new MBusException("Internal error. Abort state was not reset");
      }
      try {
        while (!eventQueue.isEmpty()) {
          postSingleEvent(eventQueue.remove(0), postingState);
        }
      } finally {
        postingState.isPosting = false;
        postingState.isMainThread = false;
      }
    }
  }


  public void cancelEventDelivery(Object event) {
    PostingThreadState postingState = currentPostingThreadState.get();
    if (!postingState.isPosting) {
      throw new MBusException(
          "This method may only be called from inside event handling methods on the posting thread");
    } else if (event == null) {
      throw new MBusException("Event may not be null");
    } else if (postingState.event != event) {
      throw new MBusException("Only the currently handled event may be aborted");
    } else if (postingState.subscription.subscriberMethod.threadMode != ThreadMode.THREADNOW) {
      throw new MBusException(" event handlers may only abort the incoming event");
    }

    postingState.canceled = true;
  }

  public void postSticky(String tag, Object params) {
    postSticky(tag, params, null);
  }


  public void postSticky(String tag, Object params, CallBack callBack) {
    synchronized (stickyEvents) {
      stickyEvents.put(tag, new StickySingle(params, callBack));
    }
    post(tag, params, callBack);
  }


  public <T> T getStickyEvent(String eventType) {
    synchronized (stickyEvents) {
      return (T) stickyEvents.get(eventType);
    }
  }

  public <T> T removeStickyEvent(String eventType) {
    synchronized (stickyEvents) {
      return (T) stickyEvents.remove(eventType);
    }
  }


  public boolean removeStickyEvent(String type, Object param) {
    synchronized (stickyEvents) {
      StickySingle existingEvent = stickyEvents.get(type);
      if (param != null && existingEvent != null && param.equals(existingEvent.params)) {
        stickyEvents.remove(type);
        return true;
      } else {
        return false;
      }
    }
  }


  public void removeAllStickyEvents() {
    synchronized (stickyEvents) {
      stickyEvents.clear();
    }
  }


  private void postSingleEvent(QueueSingle event, PostingThreadState postingState) throws Error {
    String eventType = event.eventType;
    boolean subscriptionFound;

    subscriptionFound = postSingleEventForEventType(event.params, postingState, eventType, event.callBack);
    if (!subscriptionFound) {
      if (logNoSubscriberMessages) {
        logger.log(Level.FINE, "No subscribers registered for event " + eventType);
      }
      if (sendNoSubscriberEvent) {
        post(NOEVENT, null, null);
      }
    }
  }

  private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, String eventType, CallBack callBack) {
    CopyOnWriteArrayList<Subscription> subscriptions;//某一个事件的全部订阅的方法
    synchronized (this) {
      subscriptions = subscriptionsByEventType.get(eventType);
    }
    if (subscriptions != null && !subscriptions.isEmpty()) {
      for (Subscription subscription : subscriptions) {
        if (event.getClass() != subscription.subscriberMethod.paramType) {
          continue;
        }
        postingState.subscription = subscription;
        subscription.callBack = callBack;
        boolean aborted = false;
        try {
          postToSubscription(subscription, event, postingState.isMainThread);
          aborted = postingState.canceled;
        } finally {
          postingState.subscription = null;
          postingState.canceled = false;
        }
        if (aborted) {
          break;
        }
      }
      return true;
    }
    return false;
  }

  private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
    switch (subscription.subscriberMethod.threadMode) {
      case THREADNOW:
        invokeSubscriber(subscription, event);
        break;
      case MAIN:
        if (isMainThread) {
          invokeSubscriber(subscription, event);
        } else {
          mainThreadPoster.enqueue(subscription, event);
        }
        break;
      case THREADPOOL:
        asyncPoster.enqueue(subscription, event);
        break;
      default:
        throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
    }
  }


  void invokeSubscriber(PendingPost pendingPost) {
    Object event = pendingPost.event;
    Subscription subscription = pendingPost.subscription;
    PendingPost.releasePendingPost(pendingPost);
    if (subscription.active) {
      invokeSubscriber(subscription, event);
    }
  }

  void invokeSubscriber(Subscription subscription, Object event) {
    try {
      Object object = subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
      if (subscription.callBack != null) {
        subscription.callBack.onReturn(object);
      }
    } catch (InvocationTargetException e) {
      handleSubscriberException(subscription, event, e.getCause());
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Unexpected exception", e);
    }
  }

  private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {

  }


  final static class PostingThreadState {
    final List<QueueSingle> eventQueue = new ArrayList<>();
    boolean isPosting;
    boolean isMainThread;
    Subscription subscription;
    Object event;
    boolean canceled;

  }

  final static class QueueSingle {
    public QueueSingle(String eventType, Object params, CallBack callBack) {
      this.eventType = eventType;
      this.params = params;
      this.callBack = callBack;
    }

    String eventType;
    Object params;
    CallBack callBack;
  }

  final static class StickySingle {
    public StickySingle(Object params, CallBack callBack) {
      this.params = params;
      this.callBack = callBack;
    }

    Object params;
    CallBack callBack;
  }

  ExecutorService getExecutorService() {
    return executorService;
  }

  public Logger getLogger() {
    return logger;
  }
}

