package dynamicProxy;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by twiceYuan on 11/11/2016.
 *
 * 模拟 retrofit 的请求过程
 */
public class FakeRetrofit {

    public static <T> T create(Class<T> apiClass) {
        //noinspection unchecked
        return (T) Proxy.newProxyInstance(apiClass.getClassLoader(), new Class<?>[]{apiClass}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                System.out.println("调用网络请求方法：" + method.getName());

                POST annotation = method.getAnnotation(POST.class);
                String postUrl = annotation.value();

                System.out.println("Url: " + postUrl);

                if (args != null && args.length != 0) {
                    List<String> params = new ArrayList<>();
                    Annotation[][] annotations = method.getParameterAnnotations();
                    for (int i = 0; i < args.length; i++) {
                        Annotation[] paramsAnnotation = annotations[i];
                        params.add(String.format("%s: %s", ((Param) paramsAnnotation[0]).value(), args[i].toString()));
                    }
                    System.out.printf("Params: %s\n", params.toString());
                }

                Type genericReturnType = method.getGenericReturnType();
                Class resultClass = (Class) getParameterUpperBound(0, (ParameterizedType) genericReturnType);
                System.out.println("return: type" + resultClass.getSimpleName());
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
        });
    }

    private static Type getParameterUpperBound(int index, ParameterizedType type) {
        Type[] types = type.getActualTypeArguments();
        if (index < 0 || index >= types.length) {
            throw new IllegalArgumentException(
                    "Index " + index + " not in range [0," + types.length + ") for " + type);
        }
        Type paramType = types[index];
        if (paramType instanceof WildcardType) {
            return ((WildcardType) paramType).getUpperBounds()[0];
        }
        return paramType;
    }
}
