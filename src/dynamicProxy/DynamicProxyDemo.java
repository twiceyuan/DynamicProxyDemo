package dynamicProxy;

/**
 * Created by twiceYuan on 11/11/2016.
 * <p>
 * 动态代理测试
 */
public class DynamicProxyDemo {

    public static void main(String args[]) {
        Api print = FakeRetrofit.create(Api.class);
        print.fetchUser().call(user -> {
            String result = "print.fetchUser().call => " + user.getClass().getSimpleName();
            System.out.println(result);
        });
        print.login("user1", "passw0rd").call(s -> {
            String result = "print.login(\"user1\", \"passw0rd\").call => " + s.getClass().getSimpleName();
            System.out.println(result);
        });
    }

    interface Api {
        @POST("http://example.com")
        CallAdapter<User> fetchUser();

        @POST("http://example.com")
        CallAdapter<String> login(@Param("username") String username, @Param("password") String password);
    }
}
