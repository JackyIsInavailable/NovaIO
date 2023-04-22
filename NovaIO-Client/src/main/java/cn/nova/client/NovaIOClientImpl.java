package cn.nova.client;

import cn.nova.*;
import cn.nova.client.exception.IllegalPathException;
import cn.nova.client.exception.IllegalTypeException;
import cn.nova.client.result.QueryLeaderResult;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static cn.nova.Utils.*;

/**
 * {@link NovaIOClientImpl}是{@link NovaIOClient}的默认实现类
 *
 * @author RealDragonking
 */
class NovaIOClientImpl implements NovaIOClient {

    private static final Logger log = LogManager.getLogger(NovaIOClient.class);
    private final Map<Long, AsyncFuture<?>> responseWaitMap;
    private final Queue<PendingMessage> pendingMessageQueue;
    private final AtomicBoolean updateLeaderState;
    private final ResponseHandler responseHandler;
    private final RaftClusterNode[] clusterNodes;
    private final EventLoopGroup ioThreadGroup;
    private final AtomicLong reqIDGenerator;
    private final NovaIOClientConfig config;
    private final Bootstrap bootstrap;
    private final String clusterName;
    private final Lock messageSendingLock;
    private final Timer timer;
    private Channel leaderChannel;
    private volatile boolean leaderNotSelected;
    private volatile boolean isClosed;
    private volatile long leaderTerm;

    NovaIOClientImpl(String clusterName, InetSocketAddress[] addresses, NovaIOClientConfig config) {
        ThreadFactory timerThreadFactory = getThreadFactory("Client-Timer", false);
        ThreadFactory ioThreadFactory = getThreadFactory("Client-IO", true);

        this.ioThreadGroup = new NioEventLoopGroup(config.getIoThreadNumber(), ioThreadFactory);
        this.timer = new HashedWheelTimer(timerThreadFactory);

        this.pendingMessageQueue = new ConcurrentLinkedQueue<>();
        this.responseWaitMap = new ConcurrentHashMap<>();
        this.messageSendingLock = new ReentrantLock();
        this.updateLeaderState = new AtomicBoolean();
        this.reqIDGenerator = new AtomicLong();
        this.clusterName = clusterName;
        this.config = config;

        this.leaderNotSelected = true;
        this.isClosed = false;

        int nodeSize = addresses.length;
        this.clusterNodes = new RaftClusterNode[nodeSize];

        for (int i = 0; i < nodeSize; i++) {
            clusterNodes[i ++] = new RaftClusterNode(addresses[i]);
        }

        this.responseHandler = new ResponseHandler(responseWaitMap);
        this.bootstrap = new Bootstrap()
                .group(ioThreadGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 4, 0, 4))
                                .addLast(responseHandler);
                    }
                });

        this.loopConnect();
        this.updateLeader();
    }

    /**
     * @return 连接到的目标集群的名称
     */
    @Override
    public String clusterName() {
        return this.clusterName;
    }

    /**
     * 在给定的绝对路径下，尝试创建一个文件对象。如果目标路径下已经存在同名文件对象，那么将创建失败
     *
     * @param path 绝对路径
     * @param type 文件对象的类型
     * @return 是否成功创建
     * @throws IllegalPathException 实际目标路径无效
     * @throws TimeoutException     响应超时
     */
    @Override
    public boolean createFileObject(String path, FileObjectType type) throws IllegalPathException, TimeoutException {
        return false;
    }

    /**
     * 在给定的绝对路径下，尝试删除一个文件对象
     *
     * @param path 绝对路径
     * @return 是否成功删除
     * @throws IllegalPathException 实际目标路径无效
     * @throws TimeoutException     响应超时
     */
    @Override
    public boolean deleteFileObject(String path) throws IllegalPathException, TimeoutException {
        return false;
    }

    /**
     * 在给定的绝对路径下，获取到目标文件对象的信息体
     *
     * @param path 绝对路径
     * @return {@link FileObject}
     * @throws IllegalPathException 实际目标路径无效
     * @throws TimeoutException     响应超时
     */
    @Override
    public FileObject getFileObject(String path) throws IllegalPathException, TimeoutException {
        return null;
    }

    /**
     * 在给定的绝对路径下，获取到文件夹类型的目标文件对象下，所有下一级子文件对象的信息体
     *
     * @param path 绝对路径
     * @return {@link FileObject}
     * @throws IllegalPathException 实际目标路径无效
     * @throws IllegalTypeException 实际文件对象非文件夹
     * @throws TimeoutException     响应超时
     */
    @Override
    public FileObject[] getFileObjects(String path) throws IllegalPathException, IllegalTypeException, TimeoutException {
        return new FileObject[0];
    }

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * <p> As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised
     * to relinquish the underlying resources and to internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     *
     */
    @Override
    public void close() {
        timer.stop();
        ioThreadGroup.shutdownGracefully();

        isClosed = true;
        for (RaftClusterNode node : clusterNodes) {
            node.disconnect();
        }
    }

    /**
     * 尝试获取到控制消息发送的标志位（锁），如果标志位修改失败，则加入{@link #pendingMessageQueue}等待延迟发送。
     * 请确保{@link ByteBuf}已经完成写入头部的长度字段和路径字段、requestID
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     * @param asyncFuture {@link AsyncFuture}
     */
    private void sendMessage(ByteBuf byteBuf, AsyncFuture<?> asyncFuture) {
        messageSendingLock.lock();
        if (updateLeaderState.compareAndSet(true, false)) {
            sendMessage0(byteBuf, asyncFuture);
            updateLeaderState.set(true);
        } else {
            PendingMessage pending = new PendingMessage(byteBuf, asyncFuture);
            pendingMessageQueue.offer(pending);
        }
        messageSendingLock.unlock();
    }

    /**
     * 如果{@link #pendingMessageQueue}不为空的话，从中取出一条消息调用{@link #sendMessage0(ByteBuf, AsyncFuture)}进行发送
     */
    private void flushPendingMessage() {
        if (! pendingMessageQueue.isEmpty()) {
            PendingMessage pending;
            while ((pending = pendingMessageQueue.poll()) != null) {
                sendMessage(pending.byteBuf, pending.asyncFuture);
            }
        }
    }

    /**
     * 尝试向leader节点发送一个完整的{@link ByteBuf}消息，并启动超时计时。一旦消息响应超时，
     * 那么启动{@link #updateLeader()}进程
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     * @param asyncFuture {@link AsyncFuture}
     */
    private void sendMessage0(ByteBuf byteBuf, AsyncFuture<?> asyncFuture) {
        long requestID = reqIDGenerator.incrementAndGet();
        responseWaitMap.put(requestID, asyncFuture);

        leaderChannel.writeAndFlush(byteBuf);

        asyncFuture.addListener(result -> {
            if (result == null) {
                updateLeader();
            }
        });

        timer.newTimeout(t -> {
            responseWaitMap.remove(requestID);
            asyncFuture.notifyResult(null);
        }, config.getResponseTimeout(), TimeUnit.MILLISECONDS);
    }

    /**
     * 循环检测和集群节点的{@link Channel}通信信道是否活跃，并执行重连操作
     */
    private void loopConnect() {
        if (isClosed) {
            return;
        }

        for (RaftClusterNode node : clusterNodes) {
            if (node.channel == null && node.connectMonitor.compareAndSet(false, true)) {

                ChannelFuture future = bootstrap.connect(node.address);
                Channel channel = future.channel();

                future.addListener(f -> {
                    node.setChannel(channel, f.isSuccess());
                    node.connectMonitor.set(false);
                });
            }
        }

        timer.newTimeout(t -> loopConnect(), config.getReconnectTimeInterval(), TimeUnit.MILLISECONDS);
    }

    /**
     * 尝试抢占{@link #updateLeaderState}标志锁，启动leader信息更新的进程
     */
    private void updateLeader() {
        if (isClosed) {
            return;
        }

        if (updateLeaderState.compareAndSet(true, false)) {
            if (leaderNotSelected) {
                log.info("正在初始化更新leader节点位置");
            } else {
                log.info("当前leader节点响应超时，正在进行位置更新操作...");
            }
            leaderChannel = null;
            updateLeader0();
        }
    }

    /**
     * 具体更新leader信息。向所有{@link Channel}通信信道可用的节点，通过{@link #queryLeader(RaftClusterNode, Channel, DynamicCounter)}
     * 发送leader探测消息，当收到leader声明响应时，更新本地的leader节点信息
     */
    private void updateLeader0() {
        timer.newTimeout(t -> {
            DynamicCounter counter = new DynamicCounter() {
                @Override
                public void onAchieveTarget() {
                    if (leaderChannel == null) {
                        if (! isClosed) {
                            log.info("{} 集群leader节点位置更新失败，准备稍后重试...", clusterName);
                            updateLeader0();
                        }
                    } else {
                        log.info("成功更新 {} 集群leader节点位置，位于 {}", clusterName, leaderChannel.remoteAddress());
                        leaderNotSelected = false;
                        updateLeaderState.set(true);
                        flushPendingMessage();
                    }
                }
            };

            for (RaftClusterNode node : clusterNodes) {
                queryLeader(node, node.channel, counter);
            }

            counter.setTarget();

        }, config.getReconnectTimeInterval(), TimeUnit.MILLISECONDS);
    }

    /**
     * 向目标节点发送leader探测消息，并修改对应当前探测活动的{@link DynamicCounter}的计数信息
     *
     * @param node {@link RaftClusterNode}
     * @param channel {@link Channel}
     * @param counter {@link DynamicCounter}
     */
    private void queryLeader(RaftClusterNode node, Channel channel, DynamicCounter counter) {
        if (channel != null) {
            AsyncFuture<QueryLeaderResult> asyncFuture = new AsyncFutureImpl<>();
            long requestID = reqIDGenerator.incrementAndGet();

            responseWaitMap.put(requestID, asyncFuture);

            ByteBufMessage message = ByteBufMessage
                    .build("/query-leader")
                    .doWrite(byteBuf -> byteBuf.writeLong(requestID));

            channel.writeAndFlush(message.create());
            counter.addTarget();

            asyncFuture.addListener(result -> {
                if (result == null) {
                    node.compareAndDisconnect(channel);
                } else {
                    synchronized (this) {
                        if (result.isLeader() && result.getTerm() >= leaderTerm) {
                            leaderTerm = result.getTerm();
                            leaderChannel = channel;
                        }
                    }
                }
                counter.addCount();
            });

            timer.newTimeout(t -> {
                responseWaitMap.remove(requestID);
                asyncFuture.notifyResult(null);
            }, config.getResponseTimeout(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * {@link RaftClusterNode}描述了一个raft集群节点的基本信息和连接状态，并提供了一些线程安全的方法
     * 方便我们进行状态的切换和完成一些特定操作
     *
     * @author RealDragonking
     */
    private class RaftClusterNode {

        private final AtomicBoolean connectMonitor;
        private final InetSocketAddress address;
        private Channel channel;

        private RaftClusterNode(InetSocketAddress address) {
            this.connectMonitor = new AtomicBoolean();
            this.address = address;
        }

        /**
         * 在线程安全的前提下，检查{@link #isClosed}标志位并替换当前的{@link #channel}通信信道
         *
         * @param channel {@link Channel}通信信道
         * @param isSuccess {@link Channel}是否有效（即连接创建操作是否成功）
         */
        private void setChannel(Channel channel, boolean isSuccess) {
            synchronized (this) {
                if (isSuccess) {
                    if (isClosed) {
                        channel.close();
                    } else {
                        this.channel = channel;
                        log.info("成功连接到 {} 集群中的 {} 节点", clusterName, address);
                    }
                } else {
                    if (! isClosed) {
                        log.info("无法连接到 {} 集群中的 {} 节点，准备稍后重试...", clusterName, address);
                    }
                }
            }
        }

        /**
         * 在线程安全的前提下执行通过{@link Channel#close()}完成连接断开操作
         */
        private void disconnect() {
            synchronized (this) {
                if (channel != null) {
                    channel.close();
                    channel = null;
                    log.info("客户端关闭，与 {} 集群中的 {} 节点连接自动断开", clusterName, address);
                }
            }
        }

        /**
         * 进行比较并断开操作，如果{@link #channel}不为null，并且拿来比较的{@link Channel}
         * 和{@link #channel}内存地址相同的话，我们将通过{@link Channel#close()}完成连接断开操作
         *
         * @param channel {@link Channel}通信信道
         */
        private void compareAndDisconnect(Channel channel) {
            synchronized (this) {
                if (this.channel != null && channel == this.channel) {
                    this.channel.close();
                    this.channel = null;
                    log.info("响应超时，与 {} 集群中的 {} 节点连接自动断开", clusterName, address);
                }
            }
        }

    }

    /**
     * {@link PendingMessage}描述了正在等待从客户端发送到NovaIO服务节点的消息
     *
     * @author RealDragonking
     */
    private static class PendingMessage {
        private final ByteBuf byteBuf;
        private final AsyncFuture<?> asyncFuture;
        private PendingMessage(ByteBuf byteBuf, AsyncFuture<?> asyncFuture) {
            this.byteBuf = byteBuf;
            this.asyncFuture = asyncFuture;
        }
    }

}
