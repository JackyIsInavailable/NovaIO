package cn.nova;

/**
 * {@link AsyncFuture}定义了本地或是远程计算执行的异步返回结果，可以通过添加{@link AsyncFutureListener}的方式挂载回调函数
 *
 * @author RealDragonking
 * @param <T> 响应消息的类型
 */
public interface AsyncFuture<T> {

    /**
     * 新增一个{@link AsyncFutureListener}
     *
     * @param listener {@link AsyncFutureListener}
     */
    void addListener(AsyncFutureListener<T> listener);

    /**
     * 通知异步执行结果
     *
     * @param result 执行结果
     */
    void notifyResult(T result);

}
