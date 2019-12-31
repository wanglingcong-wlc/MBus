# MBus
Changed EventBus and Arouter

### 修改版EventBus
* 注册方法和eventbus相同
* 使用String作为event的type，必须在@MBus的注解中声明
* 声明@MBus的方法必须且只能有一个参数，参数类型随意，匹配时会根据type和参数类型，找到符合的方法并调用
* 可以设置返回值的监听对象，见项目demo
* 优化了使用反射扫描类时过滤的父类中少掉的androidx包

### 简化版Arouter
* @MRoute注解到相应类中，使用即可跳转，支持携带参数，支持startActivityForResult
