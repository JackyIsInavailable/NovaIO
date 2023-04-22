package cn.nova.client;

import cn.nova.client.exception.IllegalPathException;
import cn.nova.client.exception.IllegalTypeException;

import java.io.Closeable;
import java.util.concurrent.TimeoutException;

/**
 * {@link NovaIOClient}定义了一个阻塞式客户端，能够和指定NovaIO节点集群创建连接并进行通信
 *
 * @author RealDragonking
 */
public interface NovaIOClient extends Closeable {

    /**
     * @return 连接到的目标集群的名称
     */
    String clusterName();

    /**
     * 在给定的绝对路径下，尝试创建一个文件对象。如果目标路径下已经存在同名文件对象，那么将创建失败
     *
     * @param path 绝对路径
     * @param type 文件对象的类型
     * @return 是否成功创建
     * @throws IllegalPathException 实际目标路径无效
     * @throws TimeoutException 响应超时
     */
    boolean createFileObject(String path, FileObjectType type) throws IllegalPathException, TimeoutException;

    /**
     * 在给定的绝对路径下，尝试删除一个文件对象。如果目标路径下不存在同名文件对象，那么将删除失败
     *
     * @param path 绝对路径
     * @return 是否成功删除
     * @throws IllegalPathException 实际目标路径无效
     * @throws TimeoutException 响应超时
     */
    boolean deleteFileObject(String path) throws IllegalPathException, TimeoutException;

    /**
     * 在给定的绝对路径下，获取到目标文件对象的信息体
     *
     * @param path 绝对路径
     * @return {@link FileObject}
     * @throws IllegalPathException 实际目标路径无效
     * @throws TimeoutException 响应超时
     */
    FileObject getFileObject(String path) throws IllegalPathException, TimeoutException;

    /**
     * 在给定的绝对路径下，获取到文件夹类型的目标文件对象下，所有下一级子文件对象的信息体
     *
     * @param path 绝对路径
     * @return {@link FileObject}
     * @throws IllegalPathException 实际目标路径无效
     * @throws IllegalTypeException 实际文件对象非文件夹
     * @throws TimeoutException 响应超时
     */
    FileObject[] getFileObjects(String path) throws IllegalPathException, IllegalTypeException, TimeoutException;

}
