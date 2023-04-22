package cn.nova.client;

/**
 * {@link FileObjectType}枚举了文件对象的几种类型
 *
 * @author RealDragonking
 */
public enum FileObjectType {

    DIRECTORY("dir"), FILE("file");

    private final String typeName;

    FileObjectType(String typeName) {
        this.typeName = typeName;
    }

    /**
     * Returns the name of this enum constant, as contained in the
     * declaration.  This method may be overridden, though it typically
     * isn't necessary or desirable.  An enum type should override this
     * method when a more "programmer-friendly" string form exists.
     *
     * @return the name of this enum constant
     */
    @Override
    public String toString() {
        return this.typeName;
    }

}
