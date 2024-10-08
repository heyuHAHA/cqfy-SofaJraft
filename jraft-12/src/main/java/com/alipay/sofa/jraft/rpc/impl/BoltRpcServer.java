package com.alipay.sofa.jraft.rpc.impl;

import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.ConnectionEventType;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;
import com.alipay.sofa.jraft.rpc.Connection;
import com.alipay.sofa.jraft.rpc.RpcContext;
import com.alipay.sofa.jraft.rpc.RpcProcessor;
import com.alipay.sofa.jraft.rpc.RpcServer;
import com.alipay.sofa.jraft.util.Requires;

import java.util.concurrent.Executor;

/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/11/23
 * @Description:Raft集群内部进行RPC通信时使用的服务端
 */
public class BoltRpcServer implements RpcServer {

    private final com.alipay.remoting.rpc.RpcServer rpcServer;

    public BoltRpcServer(final com.alipay.remoting.rpc.RpcServer rpcServer) {
        this.rpcServer = Requires.requireNonNull(rpcServer, "rpcServer");
    }

    @Override
    public boolean init(final Void opts) {
        this.rpcServer.option(BoltClientOption.NETTY_FLUSH_CONSOLIDATION, true);
        this.rpcServer.initWriteBufferWaterMark(BoltRaftRpcFactory.CHANNEL_WRITE_BUF_LOW_WATER_MARK,
                BoltRaftRpcFactory.CHANNEL_WRITE_BUF_HIGH_WATER_MARK);
        this.rpcServer.startup();
        return this.rpcServer.isStarted();
    }

    @Override
    public void shutdown() {
        this.rpcServer.shutdown();
    }

    @Override
    public void registerConnectionClosedEventListener(final ConnectionClosedEventListener listener) {
        this.rpcServer.addConnectionEventProcessor(ConnectionEventType.CLOSE, (remoteAddress, conn) -> {
            final Connection proxyConn = conn == null ? null : new Connection() {

                @Override
                public Object getAttribute(final String key) {
                    return conn.getAttribute(key);
                }

                @Override
                public Object setAttributeIfAbsent(final String key, final Object value) {
                    return conn.setAttributeIfAbsent(key, value);
                }

                @Override
                public void setAttribute(final String key, final Object value) {
                    conn.setAttribute(key, value);
                }

                @Override
                public void close() {
                    conn.close();
                }
            };

            listener.onClosed(remoteAddress, proxyConn);
        });
    }

    @Override
    public int boundPort() {
        return this.rpcServer.port();
    }


    //把处理器添加到服务端中的方法，这里继续向内部调用，会被这些处理器都注册到bolt框架中
    //最后在Netty的handler处理器中被调用，说到底，用的还是Netty那一套，因为bolt这个框架就是对Netty做了一层封装
    @Override
    public void registerProcessor(final RpcProcessor processor) {
        //这里面可以看到，用户自己提交的processor会被继续包装成一个AsyncUserProcessor对象
        //这个对象最后在Netty中被调用的时候，就是调用这个对象的handeRequest方法时
        //会在方法内部调用用户提交的procesor的handleRequest方法
        this.rpcServer.registerUserProcessor(new AsyncUserProcessor<Object>() {


            //服务端handler会调用这个方法，然后一层层向下调用，最后调用到Jraft框架中定义的处理器中
            //asyncCtx这个对象是从bolt框架中传递过来的，里面定义着回复响应的方法
            @SuppressWarnings("unchecked")
            @Override
            public void handleRequest(final BizContext bizCtx, final AsyncContext asyncCtx, final Object request) {
                final RpcContext rpcCtx = new RpcContext() {

                    @Override
                    public void sendResponse(final Object responseObj) {
                        asyncCtx.sendResponse(responseObj);
                    }

                    @Override
                    public Connection getConnection() {
                        com.alipay.remoting.Connection conn = bizCtx.getConnection();
                        if (conn == null) {
                            return null;
                        }
                        return new BoltConnection(conn);
                    }

                    @Override
                    public String getRemoteAddress() {
                        return bizCtx.getRemoteAddress();
                    }
                };

                processor.handleRequest(rpcCtx, request);
            }

            @Override
            public String interest() {
                return processor.interest();
            }

            @Override
            public ExecutorSelector getExecutorSelector() {
                final RpcProcessor.ExecutorSelector realSelector = processor.executorSelector();
                if (realSelector == null) {
                    return null;
                }
                return realSelector::select;
            }

            @Override
            public Executor getExecutor() {
                return processor.executor();
            }
        });
    }

    public com.alipay.remoting.rpc.RpcServer getServer() {
        return this.rpcServer;
    }

    private static class BoltConnection implements Connection {

        private final com.alipay.remoting.Connection conn;

        private BoltConnection(final com.alipay.remoting.Connection conn) {
            this.conn = Requires.requireNonNull(conn, "conn");
        }

        @Override
        public Object getAttribute(final String key) {
            return this.conn.getAttribute(key);
        }

        @Override
        public Object setAttributeIfAbsent(final String key, final Object value) {
            return this.conn.setAttributeIfAbsent(key, value);
        }

        @Override
        public void setAttribute(final String key, final Object value) {
            this.conn.setAttribute(key, value);
        }

        @Override
        public void close() {
            this.conn.close();
        }
    }
}
