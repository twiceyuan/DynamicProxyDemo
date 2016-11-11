package dynamicProxy;

public interface CallAdapter<T> {
    void call(Call<T> call);
}
