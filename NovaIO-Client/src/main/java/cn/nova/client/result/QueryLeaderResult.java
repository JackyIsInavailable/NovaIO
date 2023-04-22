package cn.nova.client.result;

/**
 * {@link QueryLeaderResult}对应于探测ViewNode节点leader身份的请求
 *
 * @author RealDragonking
 */
public class QueryLeaderResult {

    private final boolean isLeader;
    private final long term;

    public QueryLeaderResult(boolean isLeader, long term) {
        this.isLeader = isLeader;
        this.term = term;
    }

    /**
     * @return 当前节点是否是新任Leader节点
     */
    public boolean isLeader() {
        return this.isLeader;
    }

    /**
     * @return 当前节点的任期
     */
    public long getTerm() {
        return this.term;
    }

}
