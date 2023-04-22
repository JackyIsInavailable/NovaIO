package cn.nova;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.function.Consumer;

import static cn.nova.Utils.writeString;

/**
 * {@link ByteBufMessage}提供了一种更方便的方法，在写入主体消息前，往头部写入长度字段和路径字段
 *
 * @author RealDragonking
 */
public class ByteBufMessage {

    private final ByteBuf byteBuf;

    private ByteBufMessage(ByteBuf byteBuf) {
        this.byteBuf = byteBuf;
    }

    /**
     * 创建一个{@link ByteBufMessage}，往头部预留长度字段的空间
     *
     * @return {@link ByteBufMessage}
     */
    public static ByteBufMessage build() {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
        byteBuf.writerIndex(4);

        return new ByteBufMessage(byteBuf);
    }

    /**
     * 创建一个{@link ByteBufMessage}，往头部预留长度字段的空间，并写入路径字段
     *
     * @param path 映射的路径
     * @return {@link ByteBufMessage}
     */
    public static ByteBufMessage build(String path) {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
        byteBuf.writerIndex(4);

        writeString(byteBuf, path);

        return new ByteBufMessage(byteBuf);
    }

    /**
     * 在内部的{@link ByteBuf}中执行写操作
     *
     * @param writeOperation 写操作
     * @return {@link ByteBufMessage}
     */
    public ByteBufMessage doWrite(Consumer<ByteBuf> writeOperation) {
        writeOperation.accept(byteBuf);
        return this;
    }

    /**
     * 生成最终的{@link ByteBuf}
     *
     * @return {@link ByteBuf}
     */
    public ByteBuf create() {
        int writerIndex = byteBuf.writerIndex();
        byteBuf.writerIndex(0)
                .writeInt(writerIndex - 4)
                .writerIndex(writerIndex);
        return byteBuf;
    }

}