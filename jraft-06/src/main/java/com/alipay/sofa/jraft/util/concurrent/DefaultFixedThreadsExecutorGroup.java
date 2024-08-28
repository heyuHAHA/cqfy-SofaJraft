package com.alipay.sofa.jraft.util.concurrent;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

//执行器组，这个执行器组的功能也比较重要，但是在第一版本中没发挥作用
//引入日志复制后，我再为大家详细讲解
public final class DefaultFixedThreadsExecutorGroup implements FixedThreadsExecutorGroup {

    private final SingleThreadExecutor[]                 children;
    private final Set<SingleThreadExecutor> readonlyChildren;
    private final ExecutorChooserFactory.ExecutorChooser chooser;

    public DefaultFixedThreadsExecutorGroup(SingleThreadExecutor[] children) {
        this(children, DefaultExecutorChooserFactory.INSTANCE.newChooser(children));
    }

    public DefaultFixedThreadsExecutorGroup(SingleThreadExecutor[] children,
                                            ExecutorChooserFactory.ExecutorChooser chooser) {
        this.children = children;
        this.readonlyChildren = toUnmodifiableSet(this.children);
        this.chooser = chooser;
    }

    public DefaultFixedThreadsExecutorGroup(ExecutorService[] executors) {
        this.children = toSingleThreadExecutors(executors);
        this.readonlyChildren = toUnmodifiableSet(this.children);
        this.chooser = DefaultExecutorChooserFactory.INSTANCE.newChooser(this.children);
    }

    public DefaultFixedThreadsExecutorGroup(ExecutorService[] executors, ExecutorChooserFactory.ExecutorChooser chooser) {
        this.children = toSingleThreadExecutors(executors);
        this.readonlyChildren = toUnmodifiableSet(this.children);
        this.chooser = chooser;
    }

    @Override
    public SingleThreadExecutor next() {
        return this.chooser.next();
    }

    @Override
    public void execute(final int index, final Runnable task) {
        this.chooser.select(index).execute(task);
    }

    @Override
    public boolean shutdownGracefully() {
        boolean success = true;
        for (final SingleThreadExecutor c : this.children) {
            success = success && c.shutdownGracefully();
        }
        return success;
    }

    @Override
    public boolean shutdownGracefully(final long timeout, final TimeUnit unit) {
        boolean success = true;
        final long timeoutNanos = unit.toNanos(timeout);
        final long start = System.nanoTime();
        for (final SingleThreadExecutor c : this.children) {
            success = success && c.shutdownGracefully(timeout, unit);
            if (System.nanoTime() - start > timeoutNanos) {
                success = false;
                break;
            }
        }
        return success;
    }

    @Override
    public Iterator<SingleThreadExecutor> iterator() {
        return this.readonlyChildren.iterator();
    }

    private static SingleThreadExecutor[] toSingleThreadExecutors(final ExecutorService[] executors) {
        final SingleThreadExecutor[] array = new SingleThreadExecutor[executors.length];
        for (int i = 0; i < executors.length; i++) {
            if (executors[i] instanceof SingleThreadExecutor) {
                array[i] = (SingleThreadExecutor) executors[i];
            } else {
                array[i] = new DefaultSingleThreadExecutor(executors[i]);
            }
        }
        return array;
    }

    private static Set<SingleThreadExecutor> toUnmodifiableSet(final SingleThreadExecutor[] children) {
        final Set<SingleThreadExecutor> tmp = new LinkedHashSet<>();
        Collections.addAll(tmp, children);
        return Collections.unmodifiableSet(tmp);
    }
}
