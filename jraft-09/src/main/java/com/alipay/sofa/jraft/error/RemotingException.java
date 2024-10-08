package com.alipay.sofa.jraft.error;



public class RemotingException extends Exception {

    private static final long serialVersionUID = -6326244159775972292L;

    public RemotingException() {
    }

    public RemotingException(String message) {
        super(message);
    }

    public RemotingException(String message, Throwable cause) {
        super(message, cause);
    }

    public RemotingException(Throwable cause) {
        super(cause);
    }

    public RemotingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
