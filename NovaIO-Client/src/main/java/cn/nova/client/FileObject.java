package cn.nova.client;

/**
 * {@link FileObject}定义了一个文件对象，提供了相关信息
 *
 * @author RealDragonking
 */
public interface FileObject {

    /**
     * @return 名称
     */
    String name();

    /**
     * @return 绝对路径
     */
    String path();

    /**
     * @return 类型字段
     */
    FileObjectType type();

    /**
     * 获取到这个文件对象的上一次修改时间。如果此对象属于文件夹类型，那么将会返回全部子对象里最近的那个修改时间
     *
     * @return 上一次修改的时间
     */
    long modifyTime();

    /**
     * 获取到这个文件对象的空间占用总和。如果此对象属于文件夹类型，那么将会返回全部子对象的空间占用总和
     *
     * @return 空间占用总和
     */
    long totalStorageUsage();

}
