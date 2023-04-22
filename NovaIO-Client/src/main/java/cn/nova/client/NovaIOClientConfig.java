package cn.nova.client;

/**
 * {@link NovaIOClientConfig}作为一个配置类，提供了设置客户端各项参数的接口方法
 *
 * @author RealDragonking
 */
public final class NovaIOClientConfig {

    private long reconnectTimeInterval;
    private long responseTimeout;
    private int ioThreadNumber;

    public NovaIOClientConfig() {
        this.ioThreadNumber = Runtime.getRuntime().availableProcessors() << 1;
        this.reconnectTimeInterval = 3000L;
        this.responseTimeout = 5000L;
    }

    /**
     * 设置io线程的数量
     *
     * @param ioThreadNumber io线程的数量
     * @return {@link NovaIOClientConfig}
     */
    public NovaIOClientConfig setIOThreadNumber(int ioThreadNumber) {
        this.ioThreadNumber = ioThreadNumber;
        return this;
    }

    /**
     * @return io线程的数量
     */
    public int getIoThreadNumber() {
        return this.ioThreadNumber;
    }

    /**
     * 设置重新连接到节点的间隔时间
     *
     * @param timeInterval 间隔时间
     * @return {@link NovaIOClientConfig}
     */
    public NovaIOClientConfig setReconnectTimeInterval(long timeInterval) {
        this.reconnectTimeInterval = timeInterval;
        return this;
    }

    /**
     * @return 重新连接到节点的间隔时间
     */
    public long getReconnectTimeInterval() {
        return this.reconnectTimeInterval;
    }

    /**
     * 设置响应超时的时间
     *
     * @param timeout 超时时间
     * @return {@link NovaIOClientConfig}
     */
    public NovaIOClientConfig setResponseTimeout(long timeout) {
        this.responseTimeout = timeout;
        return this;
    }

    /**
     * @return 响应超时时间
     */
    public long getResponseTimeout() {
        return this.responseTimeout;
    }

}
