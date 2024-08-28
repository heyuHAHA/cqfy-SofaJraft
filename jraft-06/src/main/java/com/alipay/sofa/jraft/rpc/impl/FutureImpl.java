package com.alipay.sofa.jraft.rpc.impl;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/11/24
 * @Description:这个和Netty中的promise很相似
 */
public class FutureImpl<R> implements Future<R> {

    protected final ReentrantLock lock;

    protected boolean             isDone;

    protected CountDownLatch latch;

    protected boolean             isCancelled;
    protected Throwable           failure;

    protected R                   result;

    public FutureImpl() {
        this(new ReentrantLock());
    }

    public FutureImpl(ReentrantLock lock) {
        this.lock = lock;
        this.latch = new CountDownLatch(1);
    }

    /**
     * Get current result value without any blocking.
     *
     * @return current result value without any blocking.
     */
    public R getResult() {
        this.lock.lock();
        try {
            return this.result;
        } finally {
            this.lock.unlock();
        }
    }

    public Throwable getFailure() {
        this.lock.lock();
        try {
            return this.failure;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Set the result value and notify about operation completion.
     *
     * @param result
     *            the result value
     */
    public void setResult(R result) {
        this.lock.lock();
        try {
            this.result = result;
            notifyHaveResult();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        this.lock.lock();
        try {
            this.isCancelled = true;
            notifyHaveResult();
            return true;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCancelled() {
        try {
            this.lock.lock();
            return this.isCancelled;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDone() {
        this.lock.lock();
        try {
            return this.isDone;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public R get() throws InterruptedException, ExecutionException {
        this.latch.await();
        this.lock.lock();
        try {
            if (this.isCancelled) {
                throw new CancellationException();
            } else if (this.failure != null) {
                throw new ExecutionException(this.failure);
            }

            return this.result;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public R get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException,
            TimeoutException {
        final boolean isTimeOut = !latch.await(timeout, unit);
        this.lock.lock();
        try {
            if (!isTimeOut) {
                if (this.isCancelled) {
                    throw new CancellationException();
                } else if (this.failure != null) {
                    throw new ExecutionException(this.failure);
                }

                return this.result;
            } else {
                throw new TimeoutException();
            }
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Notify about the failure, occured during asynchronous operation
     * execution.
     */
    public void failure(final Throwable failure) {
        this.lock.lock();
        try {
            this.failure = failure;
            notifyHaveResult();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Notify blocked listeners threads about operation completion.
     */
    protected void notifyHaveResult() {
        this.isDone = true;
        this.latch.countDown();
    }
}
