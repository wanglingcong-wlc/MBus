# MBus
Changed EventBus and Arouter

### 简介
* 使用String作为event的type，也可以不填，之前的eventbus的@subscribe可以直接转为@MBus
* 声明@MBus的方法可以有0个或1个参数，参数类型**除基本类型外**随意，必须是类对象，比如Integer，匹配时会根据type和参数类型，找到符合的方法并调用
* 可以设置返回值的监听对象，也就是说@MBus注册的方法可以有返回值，见详情
* 开启索引时注解配置简化
* 优化了使用扫描父类时的过滤中少掉的androidx包，对于越来越多的包含anroidx的父类，能减少大量不必要的反射，性能提升80%
* 简便的路由跳转，@MRoute注解到相应类前，使用即可跳转，支持携带参数，支持startActivityForResult
* 支持MultiDex(Google)

### 使用方法

1.在module的build.gradle的dependencies中加入引用
```
implementation 'com.wlc:MBus:1.1.5'
annotationProcessor 'com.wlc:MBusProcessor:1.1.5'
```

2.android的defaultConfig下面加入注解配置，MBUS_USE_INDEX标识是否使用索引，使用索引可以提升性能，赋值为true或false。
```
android {    
    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments = [MBUS_MODULE_NAME: project.getName(), MBUS_USE_INDEX: 'true']
                includeCompileClasspath = true
            }
        }
    }
}
```
3.混淆文件
```
-keep class com.wlc.**{*;}
-keepattributes *Annotation*
-keepclassmembers class ** {
    @com.wlc.mbuslibs.MBus <methods>;
}
-keep @com.wlc.mroute.MRoute class * {*;}
-keep enum com.wlc.mbuslibs.ThreadMode { *; }
-ignorewarning
```
4.初始化，无论在什么位置，应首先调用一次
```
MBusMain.builder().build(Context);
```
builder可以配置各种属性，详见MBusBuilder，常用的可以设置是否不使用索引，默认是使用索引的
```
MBusMain.builder().ignoreGeneratedIndex(true).build(this);
```
如果也使用路由跳转的话，同样也需要初始化一次
```
MRouteMain.get().init(Context);
```
5.在类中使用，在方法前加入@MBus索引
* type为事件类型，使用字符串标记，在后面发送的时候使用相同字符串即可收到事件，可以为空，但是type和方法的参数不能同时为空
* ThreadMode
  * MAIN 在主线程执行
  * THREADPOOL 后台线程执行
  * THREADNOW 当前线程立即执行
* isSticky 是否为粘性事件
```
  //!!!注意不要使用这种注册
  @MBus
  public int click(String i) {//这种注册是找不到这个方法的，如果参数是String，请使用下面的方法
    
    return 3556;
  }

  @MBus(type = "login")
  public int click(String i) {//参数为String时，必须带上type的类型
    
    return 3556;
  }
```
```
  @MBus
  public int click(EventT i) {//可以不写type，但是type和param必须有一个
    
    return 3556;
  }
  
  @MBus(type = "login", threadMode = ThreadMode.MAIN, isSticky = false)
  public void click(String i) {
    
  }
  
  @MBus(type = "login")
  public boolean login() {//可以设置返回值
    
    return true;
  }
```
6.使用索引的话，在类前面使用@MRoute，path为注册的地址
```
@MRoute(path = "main")
public class MainActivity extends BaseActivity {
}
```
7.在类初始化时进行register
```
MBusMain.get().register(Object);
```
类销毁时注销
```
MBusMain.get().unregister(object);
```
8.发送事件，分别对应上面三个方法
```
MBusMain.get().post(new EventT());
MBusMain.get().post("login", "username");
MBusMain.get().post("login", null, new CallBack() {//可以接受返回值的事件
          @Override
          public void onReturn(Object o) {
            
          }
});

MBusMain.get().postSticky(new EventT()); //发送粘性事件
```
9.使用路由
```
        MRouteMain.get().build("main").navigation(context);//跳转到path为main的activity
        MRouteMain.get().build("main").withString("username", "wlc").navigation(SecondActivity.this);//携带参数
        //使用startActivityForResult，requestCode为1001
        MRouteMain.get().build("main").withString("username", "wlc").navigation(SecondActivity.this, 1001);
```
### EventBus替换为MBus的步骤
全局搜索
```
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

EventBus.getDefault()

 @Subscribe
```
**分别替换为**
```
import com.wlc.mbuslibs.MBusMain;
import com.wlc.mbuslibs.MBus;
import com.wlc.mbuslibs.ThreadMode;

MBusMain.get()

@MBus
```
