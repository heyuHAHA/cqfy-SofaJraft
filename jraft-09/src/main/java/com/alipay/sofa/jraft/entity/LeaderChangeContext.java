package com.alipay.sofa.jraft.entity;

import com.alipay.sofa.jraft.Status;

//这个类在当前版本还用不上
public class LeaderChangeContext {

    private PeerId leaderId;
    private long   term;
    private Status status;

    public LeaderChangeContext(PeerId leaderId, long term, Status status) {
        super();
        this.leaderId = leaderId;
        this.term = term;
        this.status = status;
    }

    public PeerId getLeaderId() {
        return this.leaderId;
    }

    public void setLeaderId(PeerId leaderId) {
        this.leaderId = leaderId;
    }

    public long getTerm() {
        return this.term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.leaderId == null ? 0 : this.leaderId.hashCode());
        result = prime * result + (this.status == null ? 0 : this.status.hashCode());
        result = prime * result + (int) (this.term ^ this.term >>> 32);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        LeaderChangeContext other = (LeaderChangeContext) obj;
        if (this.leaderId == null) {
            if (other.leaderId != null) {
                return false;
            }
        } else if (!this.leaderId.equals(other.leaderId)) {
            return false;
        }
        if (this.status == null) {
            if (other.status != null) {
                return false;
            }
        } else if (!this.status.equals(other.status)) {
            return false;
        }
        return this.term == other.term;
    }

    @Override
    public String toString() {
        return "LeaderChangeContext [leaderId=" + this.leaderId + ", term=" + this.term + ", status=" + this.status
                + "]";
    }

}
