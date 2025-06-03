package com.jpmc.moneytransfer.moneytransfer.transfer.service;

public class TransferRuntimeException extends RuntimeException {

    private final  Reason reason;

    public TransferRuntimeException(TransferRuntimeException.Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public TransferRuntimeException(TransferRuntimeException.Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    //Runtime exception reasons
    public enum Reason {
        UNKNOWN_ERROR,
        INVALID_ARGUMENT,
        INVALID_ACCOUNT_STATE,
        FEE_CALCULATION_FAILED;
    }
}
