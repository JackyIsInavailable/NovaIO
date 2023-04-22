package cn.nova;

import com.github.artbits.quickio.api.KV;
import com.github.artbits.quickio.core.QuickIO;
import io.netty.buffer.ByteBuf;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.ThreadLocalRandom;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static cn.nova.Utils.*;

/**
 * {@link RaftStateMachine}的抽象实现类，将{@link #applyEntryData(long, ByteBuf, AsyncFuture)}保留给子类，进行可靠数据应用逻辑的
 * 具体实现。这里我们的实现采用无锁化设计，由一个线程进行事件循环模式的状态机输入处理
 *
 * @author RealDragonking
 */
public abstract class AbstractRaftStateMachine implements RaftStateMachine {

    private static final Logger log = LogManager.getLogger(RaftStateMachine.class);
    private final PersistentEntityStore entityStore;
    private final Queue<Runnable> taskQueue;
    private final EventExecutor executor;
    private final UDPService udpService;
    private final Timer timer;
    private final KV kvStore;

    private final int index;
    private final int majority;
    private final int minElectTimeoutTicks;
    private final int maxElectTimeoutTicks;

    private volatile boolean notClosed;
    private volatile long currentTerm;
    private volatile int resetVoteWaitTicks;
    private volatile int voteWaitTicks;
    private volatile int sendWaitTicks;

    private RaftState state;

    public AbstractRaftStateMachine(EventExecutor executor, UDPService udpService, Configure config) {
        ThreadFactory timerThreadFactory = getThreadFactory("StateMachine-Timer", false);
        this.timer = new HashedWheelTimer(timerThreadFactory, 50, TimeUnit.MILLISECONDS);

        this.entityStore = PersistentEntityStores.newInstance("entry-data");
        this.taskQueue = new ConcurrentLinkedQueue<>();
        this.kvStore = QuickIO.usingKV(".");
        this.udpService = udpService;
        this.executor = executor;

        this.index = 0;
        this.majority = 0;
        this.minElectTimeoutTicks = 0;
        this.maxElectTimeoutTicks = 0;

        this.state = RaftState.FOLLOWER;
        this.notClosed = true;
    }

    /**
     * 启动此{@link RaftStateMachine}的进程
     */
    @Override
    public void start() throws Exception {
        Thread workerThread = new StateMachineThread();
        workerThread.start();

        TimerTask timerTask = new StateMachineTimerTask();
        timerTask.run(null);
    }

    /**
     * 处理来自其它节点的选票获取请求
     *
     * @param candidateIndex   竞选者节点的序列号
     * @param requestTerm      竞选者节点发出请求时的任期
     * @param requestLastIndex 竞选者节点发出请求时已写盘的最后一条Entry序列号
     */
    @Override
    public void recvVoteRequest(int candidateIndex, long requestTerm, long requestLastIndex) {

    }

    /**
     * 处理来自其它节点的选票获取响应
     *
     * @param isSuccess        是否成功获取到选票
     * @param requestTerm      当前节点发出请求时的任期
     * @param requestLastIndex 当前节点发出请求时已写盘的最后一条Entry序列号
     * @param otherTerm        选民节点处理请求时的任期
     * @param otherLastIndex   选民节点处理请求时已写盘的最后一条Entry序列号
     */
    @Override
    public void recvVoteResponse(boolean isSuccess, long requestTerm, long requestLastIndex, long otherTerm, long otherLastIndex) {

    }

    /**
     * 接收来自Leader节点的通信消息
     *
     * @param leaderIndex      leader节点的序列号
     * @param leaderTerm       leader节点的任期
     * @param enableApplyIndex 允许当前节点应用的Entry序列号
     * @param globalApplyIndex 当前全局最新的已应用Entry序列号
     * @param entryIndex       仅在Entry同步消息中出现，同步的Entry序列号
     * @param entryData        仅在Entry同步消息中出现，同步的Entry数据
     */
    @Override
    public void recvMessage(int leaderIndex, long leaderTerm, long enableApplyIndex, long globalApplyIndex,
                            long entryIndex, ByteBuf entryData) {

    }

    /**
     * 接收来自其它节点的通信消息响应
     *
     * @param otherIndex      其他节点的序列号
     * @param otherApplyIndex 其他节点的已应用Entry序列号
     * @param otherLastIndex  其他节点的已写盘的最后一条Entry序列号
     */
    @Override
    public void recvMessageResponse(int otherIndex, long otherApplyIndex, long otherLastIndex) {

    }

    /**
     * 将{@link ByteBuf}字节缓冲区中的数据写入整个集群
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     * @return {@link AsyncFuture}
     */
    @Override
    public <T> AsyncFuture<T> writeDataToCluster(ByteBuf byteBuf) {
        return null;
    }

    /**
     * @return 当前Raft状态机的部分状态切面信息
     */
    @Override
    public RaftRuntimeStatePoint getCurrentState() {
        return null;
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
    public void close() throws IOException {
        notClosed = false;
        timer.stop();
        kvStore.close();
        udpService.close();
        entityStore.close();
    }

    /**
     * 使用给定的字段范围随机化一个follower参与竞选时间/candidate选举超时时间
     *
     * @return follower参与竞选时间/candidate选举超时时间
     */
    private int randomElectTicks() {
        return ThreadLocalRandom.current().nextInt(minElectTimeoutTicks, maxElectTimeoutTicks);
    }

    /**
     * {@link StateMachineThread}通过单线程事件循环模式，处理输入状态机的所有内容。这一设计保证了无锁化和线程安全性，我们认为是有意义的
     *
     * @author RealDragonking
     */
    private class StateMachineThread extends Thread {

        private StateMachineThread() {
            super("StateMachine-Thread");
        }

        /**
         * 实现{@link Runnable#run()}方法，通过事件循环的模式，处理以{@link Runnable}为载体的输入事件
         */
        @Override
        public void run() {
            Runnable task;
            while (notClosed) {
                task = taskQueue.poll();
                if (task != null) {
                    task.run();
                }
            }
        }

    }

    /**
     * {@link StateMachineThread}实现了{@link TimerTask#run(Timeout)}方法，控制着状态机的定时行为：
     * <ul>
     *     <li>
     *         在leader状态下，定时减少{@link #sendWaitTicks}，为0时发送心跳控制消息、Entry同步消息
     *     </li>
     *     <li>
     *         在follower状态下，定时减少{@link #voteWaitTicks}，为0时发起选举进程
     *     </li>
     * </ul>
     *
     * @author RealDragonking
     */
    private class StateMachineTimerTask implements TimerTask {

        /**
         * 实现{@link TimerTask#run(Timeout)}方法，具体执行状态机的定时行为
         *
         * @param timeout a handle which is associated with this task
         */
        @Override
        public void run(Timeout timeout) {
            taskQueue.offer(() -> {
               switch (state) {
                   case LEADER :
                       break;
                   case FOLLOWER :
                       break;
                   case CANDIDATE :
                       break;
               }
               timer.newTimeout(this, 0, TimeUnit.MILLISECONDS);
            });
        }

    }

}
