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
        COULD_NOT_SAVE_TRANSFER,
        DB_CONSTRAINT_VIOLATION,
        INVALID_AMOUNT,
        UNKNOWN_ERROR,
        DB_ERROR, NOT_FOUND,
        INVALID_ARGUMENT,
        INVALID_ACCOUNT_STATE
        ;
    }
}
