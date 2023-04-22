package cn.nova;

/**
 * {@link AsyncFutureListener}定义了对{@link AsyncFuture}异步返回结果的监听，本质上是一个功能性的回调函数
 *
 * @author RealDragonking
 * @param <T> 响应消息的类型
 */
public interface AsyncFutureListener<T> {

    /**
     * 通知异步返回结果
     *
     * @param result 异步返回结果
     */
    void onNotify(T result);

}
