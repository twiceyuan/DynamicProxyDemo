Retrofit 是当前 Android 最流行的 HTTP 网络库之一了，其使用方式比较特殊，是通过定义一个接口类，通过给接口中方法和方法参数添加注解的方式来定义网络请求接口。这种风格下定义一个网络接口变得很简单。不过 Retrofit 是如何使用一个接口的 Class 创建出来实现了该接口的对象呢？最近因为工作原因想封装项目中的网络请求部分，在解决获取泛型嵌套问题的时候，一直没有找到比较理想的方案，所以拜读了 Retrofit 的源码看看这个明星网络库是如何实现这一黑科技的。

## Java 的动态代理机制

在 Retrofit 2.0 中，create 方法中是这样定义的：

```java
public <T> T create(final Class<T> service) {
    Utils.validateServiceInterface(service);
    if (validateEagerly) {
      eagerlyValidateMethods(service);
    }
    return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] { service },
        new InvocationHandler() {
          private final Platform platform = Platform.get();

          @Override public Object invoke(Object proxy, Method method, Object... args)
              throws Throwable {
            //...
          }
        });
  }
```

前两段的`Utils.validateServiceInterface(service)`和`eagerlyValidateMethods(service)`从命名可以看出是验证传入接口合法性的，所以跳过。

接下来调用了一个关键方法 `Proxy.newProxyInstance()`，这个方法就是创建一个代理实例的方法，该方法需要三个参数，ClassLoader loader, Class<?>[] interfaces, InvocationHandler h，其中 interfaces 放置了我们传入的接口 class，InvocationHandler 也是一个接口，只有一个回调方法，也回调了三个参数：Object proxy, Method method, Object... args：其中 proxy 就是构造出来的代理实例，使用 proxy 调用一个方法时，这里传进来的 mthod 就是该方法，而 args 就是这个参数。

空说无凭，使用代码测试一下：

首先假装已经有个一个 retrofit，来定义一个接口类

```java
interface Api {
  @POST("http://example.com")
  CallAdapter<User> fetchUser();

  @POST("http://example.com")
  CallAdapter<String> login(@Param("username") String username, @Param("password") String password);
}
```
跟真的一样。

接下来 retrofit 调用了一个 create 方法返回的 Api 对象就让它能 fetchUser，能 login 了。那么可以想象一下，如果需要实现这些事情，它需要从这个接口定义中获取什么？

首先是 @POST，它让 retrofit 知道了这个请求是使用 post，然后里面的值是一个 PATH(这里为了实现简单直接放上了完整的路径)，通过它和构造 retrofit 对象时的 baseUrl 就得到了接口的完整 URL。再后面需要知道定义的参数，每个注解对应的是一个参数，同时注解里标注了这个参数的名字，所以这里获取的应该是所有参数注解值和参数的值。知道这些也就万事俱备了，接下来 retrofit 只要把请求构造出来交给 OkHttp 然后等待返回结果再传给返回值。

为了获得上面所说的信息，在 `invoke`方法里尝试以下代码：

```java
@Override
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
  // 打印调用的请求方法
  System.out.println("调用的请求方法：" + method.getName());

  // 这里获取了方法的注解，retrofit 就是在这里判断使用的哪种请求方式，以及获取 path
  POST annotation = method.getAnnotation(POST.class);
  String postUrl = annotation.value();
  System.out.println("Url: " + postUrl);

  // 非法判断是个麻烦的事情，这里我们只实现例子中 api 的解析，所以以下代码不进行异常判断。
  if (args != null && args.length != 0) {
    List<String> params = new ArrayList<>();
    // 获取所有参数的所有注解。得到的是一个二维数组，前面一个下标代表的是第几个参数，后面下标代表的是这个参数的所有注解。这里我们每个参数只定义了一个注解，所以直接取了[0]
    Annotation[][] annotations = method.getParameterAnnotations();
    for (int i = 0; i < args.length; i++) {
      Annotation[] paramsAnnotation = annotations[i];
      // 取到 Param 注解中的值，这个值在 retrofit 中一般是参数的键，而 args[i] 则是参数的值
      params.add(String.format("%s: %s", ((Param) paramsAnnotation[0]).value(), args[i].toString()));
    }
    // 打印所有参数
    System.out.printf("Params: %s\n", params.toString());
  }

  // 下面的内容可能更加关键：如何获得返回值类型。
  // 我们用了默认的 CallAdapter 来实现

  // 首先获得 CallAdapter 里定义的泛型，也就是我们最终需要的数据 class
  Type genericReturnType = method.getGenericReturnType(); // 这里获得的是最外层，也就是 CallAdapter
  // 这里偷懒使用了 Retrofit 工具类中的方法来获取 CallAdapter 的泛型。这边为了显示方便又强制转换成了 Class 对象，实际上例如 Gson，直接传入 Type 就可以解析出实体了不需要再转换为 Class
  Class resultClass = (Class) getParameterUpperBound(0, (ParameterizedType) genericReturnType);
  System.out.println("return: type" + resultClass.getSimpleName());
  // 返回调用需要的实体。这里为了篇幅没有再模拟网络请求，而只是调用了 newInstance 来创建一个对象回调出去。
  return (CallAdapter<T>) call -> {
    if (call != null) {
      try {
        //noinspection unchecked
        call.call((T) resultClass.newInstance());
      } catch (InstantiationException | IllegalAccessException e) {
        e.printStackTrace();
      }
    }
  };
}
```

好了验证一下结果：

```java
Api print = create(Api.class);
print.fetchUser().call(user -> {
  String result = "print.fetchUser().call => " + user.getClass().getSimpleName();
  System.out.println(result);
});
print.login("user1", "passw0rd").call(s -> {
  String result = "print.login(\"user1\", \"passw0rd\").call => " + s.getClass().getSimpleName();
  System.out.println(result);
});
```

打印结果：

```
调用请求方法：fetchUser
Url: http://example.com
return: typeUser
print.fetchUser().call => User
调用请求方法：login
Url: http://example.com
Params: [username: user1, password: passw0rd]
return: typeString
print.login("user1", "passw0rd").call => String
```

完全符合预期。有了这些参数，Retrofit 就可以统一封装网络请求进行处理后返回给用户定义的方法了。

## 结语

实际 Retrofit 这个模块的实现要比本文中复杂的多，不仅考虑到各个模块的解耦，对于各种请求的支持也远远超出想象。感谢 Square 提供给开源社区和 Android 这么优秀的网络库，虽然在暂时用不到它，但通过阅读源码还是能对自己有很大帮助。

