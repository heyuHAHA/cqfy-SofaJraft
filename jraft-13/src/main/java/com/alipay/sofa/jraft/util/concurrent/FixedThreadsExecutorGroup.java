package com.alipay.sofa.jraft.util.concurrent;

import java.util.concurrent.TimeUnit;


/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/11/25
 * @Description:执行器组接口
 */
public interface FixedThreadsExecutorGroup extends Iterable<SingleThreadExecutor> {

    /**
     * Returns one of the {@link SingleThreadExecutor}s managed by this
     * {@link FixedThreadsExecutorGroup}.
     */
    SingleThreadExecutor next();

    /**
     * Executes the given task at some time in the future.  The task
     * execute by a specified thread, which is selected by index.
     *
     * @param index index for thread chooser
     * @param task  the runnable task
     */
    void execute(final int index, final Runnable task);

    /**
     * Shortcut method for {@link #shutdownGracefully(long, TimeUnit)} with
     * sensible default values.
     *
     * @return true if success to shutdown
     */
    boolean shutdownGracefully();

    /**
     * Signals all executors that the caller wants them to be shutdown.
     *
     * @param timeout the maximum amount of time to wait until the executor
     *                is shutdown
     * @param unit    the unit of {@code timeout}
     * @return true if success to shutdown
     */
    boolean shutdownGracefully(final long timeout, final TimeUnit unit);
}
