package com.alipay.sofa.jraft.entity;

import com.alipay.sofa.jraft.util.Bits;
import com.alipay.sofa.jraft.util.Copiable;
import com.alipay.sofa.jraft.util.CrcUtil;

import java.io.Serializable;

//该类的对象就是日志ID
public class LogId implements Comparable<LogId>, Copiable<LogId>, Serializable, Checksum {

    private static final long serialVersionUID = -6680425579347357313L;

    private long index;
    private long term;

    @Override
    public LogId copy() {
        return new LogId(this.index, this.term);
    }

    @Override
    public long checksum() {
        byte[] bs = new byte[16];
        Bits.putLong(bs, 0, this.index);
        Bits.putLong(bs, 8, this.term);
        return CrcUtil.crc64(bs);
    }

    public LogId() {
        this(0, 0);
    }

    public LogId(final long index, final long term) {
        super();
        setIndex(index);
        setTerm(term);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (this.index ^ (this.index >>> 32));
        result = prime * result + (int) (this.term ^ (this.term >>> 32));
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LogId other = (LogId) obj;
        if (this.index != other.index) {
            return false;
        }
        // noinspection RedundantIfStatement
        if (this.term != other.term) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(final LogId o) {
        // Compare term at first
        final int c = Long.compare(getTerm(), o.getTerm());
        if (c == 0) {
            return Long.compare(getIndex(), o.getIndex());
        } else {
            return c;
        }
    }

    public long getTerm() {
        return this.term;
    }

    public void setTerm(final long term) {
        this.term = term;
    }

    public long getIndex() {
        return this.index;
    }

    public void setIndex(final long index) {
        this.index = index;
    }

    @Override
    public String toString() {
        return "LogId [index=" + this.index + ", term=" + this.term + "]";
    }

}
