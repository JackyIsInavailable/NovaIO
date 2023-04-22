package cn.nova;

/**
 * <p>{@link DynamicCounter}实现了一个动态计数器，触发执行的目标值是可以动态增加的，直到调用了{@link #setTarget()}。
 * 在调用{@link #setTarget()}之前，实际计数哪怕达到了目标值也不会触发执行。</p>
 * <p>{@link DynamicCounter}可以很好的和多个{@link AsyncFuture}配合起来，实现全体响应后执行对应任务的功能。</p>
 *
 * @author RealDragonking
 */
public abstract class DynamicCounter {

    private volatile int count;
    private volatile int target;
    private volatile boolean hasDetermine;

    public DynamicCounter() {
        this.count = 0;
        this.target = 0;
        this.hasDetermine = false;
    }

    /**
     * 增加目标值
     */
    public void addTarget() {
        target ++;
    }

    /**
     * 增加实际计数
     */
    public void addCount() {
        synchronized (this) {
            count ++;
            if (hasDetermine && count == target) {
                onAchieveTarget();
            }
        }
    }

    /**
     * 声明目标值已经完成确定
     */
    public void setTarget() {
        synchronized (this) {
            hasDetermine = true;
            if (count == target) {
                onAchieveTarget();
            }
        }
    }

    /**
     * 在计数达到目标值后执行回调任务
     */
    public abstract void onAchieveTarget();

}