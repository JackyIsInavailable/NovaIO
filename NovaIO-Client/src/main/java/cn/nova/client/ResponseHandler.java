package cn.nova.client;

import cn.nova.AsyncFuture;
import io.netty.channel.*;

import java.util.Map;

/**
 * {@link ResponseHandler}负责解码来自NovaIO服务节点的响应消息体，并执行对{@link cn.nova.AsyncFuture}的消息通知
 *
 * @author RealDragonking
 */
class ResponseHandler extends ChannelInboundHandlerAdapter {

    private final Map<Long, AsyncFuture<?>> responseWaitMap;

    ResponseHandler(Map<Long, AsyncFuture<?>> responseWaitMap) {
        this.responseWaitMap = responseWaitMap;
    }

    /**
     * Calls {@link ChannelHandlerContext#fireExceptionCaught(Throwable)} to forward
     * to the next {@link ChannelHandler} in the {@link ChannelPipeline}.
     * <p>
     * Subclasses may override this method to change behavior.
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param cause {@link Throwable}异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelRead(Object)} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Subclasses may override this method to change behavior.
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param msg {@link io.netty.buffer.ByteBuf}
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

    }

}
