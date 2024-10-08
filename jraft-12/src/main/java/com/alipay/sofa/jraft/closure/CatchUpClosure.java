package com.alipay.sofa.jraft.closure;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;

import java.util.concurrent.ScheduledFuture;

public abstract class CatchUpClosure implements Closure {

    private long maxMargin;
    private ScheduledFuture<?> timer;
    private boolean hasTimer;
    private boolean errorWasSet;

    private final Status status = Status.OK();

    public Status getStatus() {
        return this.status;
    }

    public long getMaxMargin() {
        return this.maxMargin;
    }

    public void setMaxMargin(long maxMargin) {
        this.maxMargin = maxMargin;
    }

    public ScheduledFuture<?> getTimer() {
        return this.timer;
    }

    public void setTimer(ScheduledFuture<?> timer) {
        this.timer = timer;
        this.hasTimer = true;
    }

    public boolean hasTimer() {
        return this.hasTimer;
    }

    public boolean isErrorWasSet() {
        return this.errorWasSet;
    }

    public void setErrorWasSet(boolean errorWasSet) {
        this.errorWasSet = errorWasSet;
    }
}
