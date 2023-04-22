package cn.nova;

import io.netty.buffer.ByteBuf;

import java.io.Closeable;

/**
 * {@link RaftStateMachine}定义了Raft分布式共识算法的全部细节，方便进行框架化的实现。这里我们采用状态机的设计模式，
 * 提供了很多不带返回值的方法，实现从外部输入数据，更改{@link RaftStateMachine}状态机的时序状态
 *
 * @author RealDragonking
 */
public interface RaftStateMachine extends Closeable {

    /**
     * 启动此{@link RaftStateMachine}的进程
     * @throws Exception 任何启动过程中出现的异常，都应该被合理地处理
     */
    void start() throws Exception;

    /**
     * 处理来自其它节点的选票获取请求
     *
     * @param candidateIndex 竞选者节点的序列号
     * @param requestTerm 竞选者节点发出请求时的任期
     * @param requestLastIndex 竞选者节点发出请求时已写盘的最后一条Entry序列号
     */
    void recvVoteRequest(int candidateIndex, long requestTerm, long requestLastIndex);

    /**
     * 处理来自其它节点的选票获取响应
     *
     * @param isSuccess 是否成功获取到选票
     * @param requestTerm 当前节点发出请求时的任期
     * @param requestLastIndex 当前节点发出请求时已写盘的最后一条Entry序列号
     * @param otherTerm 选民节点处理请求时的任期
     * @param otherLastIndex 选民节点处理请求时已写盘的最后一条Entry序列号
     */
    void recvVoteResponse(boolean isSuccess, long requestTerm, long requestLastIndex, long otherTerm, long otherLastIndex);

    /**
     * 接收来自Leader节点的通信消息
     *
     * @param leaderIndex leader节点的序列号
     * @param leaderTerm leader节点的任期
     * @param enableApplyIndex 允许当前节点应用的Entry序列号
     * @param globalApplyIndex 当前全局最新的已应用Entry序列号
     * @param entryIndex 仅在Entry同步消息中出现，同步的Entry序列号
     * @param entryData 仅在Entry同步消息中出现，同步的Entry数据
     */
    void recvMessage(int leaderIndex, long leaderTerm, long enableApplyIndex, long globalApplyIndex,
                     long entryIndex, ByteBuf entryData);

    /**
     * 接收来自其它节点的通信消息响应
     *
     * @param otherIndex 其他节点的序列号
     * @param otherApplyIndex 其他节点的已应用Entry序列号
     * @param otherLastIndex 其他节点的已写盘的最后一条Entry序列号
     */
    void recvMessageResponse(int otherIndex, long otherApplyIndex, long otherLastIndex);

    /**
     * 将已经写入到集群大多数节点、当前节点可以应用的Entry数据进行应用
     *
     * @param entryIndex 可以应用的Entry序列号
     * @param entryData 可以应用的Entry数据
     * @param asyncFuture {@link AsyncFuture}，仅在Leader节点状态下不为null
     */
    void applyEntryData(long entryIndex, ByteBuf entryData, AsyncFuture<?> asyncFuture);

    /**
     * 将{@link ByteBuf}字节缓冲区中的数据写入整个集群
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     * @return {@link AsyncFuture}
     * @param <T> 异步返回结果的类型
     */
    <T> AsyncFuture<T> writeDataToCluster(ByteBuf byteBuf);

    /**
     * @return 当前Raft状态机的部分状态切面信息
     */
    RaftRuntimeStatePoint getCurrentState();

    /**
     * {@link RaftRuntimeStatePoint}提供了{@link RaftStateMachine}运行时的部分状态切面信息
     *
     * @author RealDragonking
     */
    interface RaftRuntimeStatePoint {

        /**
         * @return 当前节点是否成为了Leader
         */
        boolean isLeader();

        /**
         * @return 当前节点所处的任期
         */
        long currentTerm();

    }

    /**
     * {@link RaftState}枚举了{@link cn.nova.RaftStateMachine}运行过程中的几种状态
     *
     * @author RealDragonking
     */
    enum RaftState {

        LEADER, FOLLOWER, CANDIDATE

    }


}
