package com.alipay.sofa.jraft.option;

import com.alipay.sofa.jraft.core.BallotBox;
import com.alipay.sofa.jraft.core.NodeImpl;
import com.alipay.sofa.jraft.core.Scheduler;
import com.alipay.sofa.jraft.rpc.RaftClientService;
import com.alipay.sofa.jraft.storage.LogManager;

/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/11/24
 * @Description:复制器组对象需要的配置参数对象
 */
public class ReplicatorGroupOptions {

    private int heartbeatTimeoutMs;

    private int electionTimeoutMs;

    private NodeImpl node;

    private LogManager logManager;

    private BallotBox ballotBox;

    private RaftClientService raftRpcClientService;

    private RaftOptions raftOptions;

    private Scheduler timerManager;


    public Scheduler getTimerManager() {
        return this.timerManager;
    }

    public void setTimerManager(Scheduler timerManager) {
        this.timerManager = timerManager;
    }

    public RaftOptions getRaftOptions() {
        return this.raftOptions;
    }

    public void setRaftOptions(RaftOptions raftOptions) {
        this.raftOptions = raftOptions;
    }

    public RaftClientService getRaftRpcClientService() {
        return this.raftRpcClientService;
    }

    public void setRaftRpcClientService(RaftClientService raftRpcService) {
        this.raftRpcClientService = raftRpcService;
    }

    public int getHeartbeatTimeoutMs() {
        return this.heartbeatTimeoutMs;
    }

    public void setHeartbeatTimeoutMs(int heartbeatTimeoutMs) {
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
    }

    public int getElectionTimeoutMs() {
        return this.electionTimeoutMs;
    }

    public void setElectionTimeoutMs(int electionTimeoutMs) {
        this.electionTimeoutMs = electionTimeoutMs;
    }

    public BallotBox getBallotBox() {
        return this.ballotBox;
    }

    public void setBallotBox(BallotBox ballotBox) {
        this.ballotBox = ballotBox;
    }

    public NodeImpl getNode() {
        return this.node;
    }

    public void setNode(NodeImpl node) {
        this.node = node;
    }

    public LogManager getLogManager() {
        return this.logManager;
    }

    public void setLogManager(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public String toString() {
        return "ReplicatorGroupOptions{" + "heartbeatTimeoutMs=" + heartbeatTimeoutMs + ", electionTimeoutMs="
                + electionTimeoutMs + ", logManager=" + logManager + ", node=" + node
                + ", raftRpcClientService=" + raftRpcClientService
                + ", raftOptions=" + raftOptions + ", timerManager=" + timerManager + '}';
    }
}
