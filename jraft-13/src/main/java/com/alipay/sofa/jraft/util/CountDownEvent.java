package com.alipay.sofa.jraft.util;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CountDownEvent {

    private int             state    = 0;
    private final Lock lock     = new ReentrantLock();
    private final Condition busyCond = this.lock.newCondition();
    private volatile Object attachment;

    public Object getAttachment() {
        return this.attachment;
    }

    public void setAttachment(final Object attachment) {
        this.attachment = attachment;
    }

    public int incrementAndGet() {
        this.lock.lock();
        try {
            return ++this.state;
        } finally {
            this.lock.unlock();
        }
    }

    public void countDown() {
        this.lock.lock();
        try {
            if (--this.state == 0) {
                this.busyCond.signalAll();
            }
        } finally {
            this.lock.unlock();
        }
    }

    public void await() throws InterruptedException {
        this.lock.lock();
        try {
            while (this.state > 0) {
                this.busyCond.await();
            }
        } finally {
            this.lock.unlock();
        }
    }
}
