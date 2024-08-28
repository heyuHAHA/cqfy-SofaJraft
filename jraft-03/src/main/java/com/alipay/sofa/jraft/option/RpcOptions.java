package com.alipay.sofa.jraft.option;

import com.codahale.metrics.MetricRegistry;
import com.alipay.sofa.jraft.util.concurrent.FixedThreadsExecutorGroup;


//该类对象封装的是RPC远程调用需要的配置参数
public class RpcOptions {

    //RPC连接超时时间
    private int rpcConnectTimeoutMs = 1000;

    //RPC请求默认的超时时间
    private int rpcDefaultTimeout = 5000;

    //安装快照的RPC请求的默认超时时间
    private int rpcInstallSnapshotTimeout = 5 * 60 * 1000;

    //RPC业务线程池的默认大小
    private int rpcProcessorThreadPoolSize = 80;

    //是否启动校验和功能
    private boolean enableRpcChecksum = false;

    //提供RPC性能监控功能的成员变量
    private MetricRegistry metricRegistry;

    //用于appendEntries请求收到响应后回调对应方法的执行器组
    private FixedThreadsExecutorGroup appendEntriesExecutors;

    public int getRpcConnectTimeoutMs() {
        return this.rpcConnectTimeoutMs;
    }

    public void setRpcConnectTimeoutMs(int rpcConnectTimeoutMs) {
        this.rpcConnectTimeoutMs = rpcConnectTimeoutMs;
    }

    public int getRpcDefaultTimeout() {
        return this.rpcDefaultTimeout;
    }

    public void setRpcDefaultTimeout(int rpcDefaultTimeout) {
        this.rpcDefaultTimeout = rpcDefaultTimeout;
    }

    public int getRpcInstallSnapshotTimeout() {
        return rpcInstallSnapshotTimeout;
    }

    public void setRpcInstallSnapshotTimeout(int rpcInstallSnapshotTimeout) {
        this.rpcInstallSnapshotTimeout = rpcInstallSnapshotTimeout;
    }

    public int getRpcProcessorThreadPoolSize() {
        return this.rpcProcessorThreadPoolSize;
    }

    public void setRpcProcessorThreadPoolSize(int rpcProcessorThreadPoolSize) {
        this.rpcProcessorThreadPoolSize = rpcProcessorThreadPoolSize;
    }

    public boolean isEnableRpcChecksum() {
        return enableRpcChecksum;
    }

    public void setEnableRpcChecksum(boolean enableRpcChecksum) {
        this.enableRpcChecksum = enableRpcChecksum;
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    public FixedThreadsExecutorGroup getAppendEntriesExecutors() {
        return appendEntriesExecutors;
    }

    public void setAppendEntriesExecutors(FixedThreadsExecutorGroup appendEntriesExecutors) {
        this.appendEntriesExecutors = appendEntriesExecutors;
    }

    @Override
    public String toString() {
        return "RpcOptions{" + "rpcConnectTimeoutMs=" + rpcConnectTimeoutMs + ", rpcDefaultTimeout="
                + rpcDefaultTimeout + ", rpcInstallSnapshotTimeout=" + rpcInstallSnapshotTimeout
                + ", rpcProcessorThreadPoolSize=" + rpcProcessorThreadPoolSize + ", enableRpcChecksum="
                + enableRpcChecksum + ", metricRegistry=" + metricRegistry + '}';
    }
}
