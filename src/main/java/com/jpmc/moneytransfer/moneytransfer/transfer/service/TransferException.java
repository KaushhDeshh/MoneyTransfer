package com.jpmc.moneytransfer.moneytransfer.transfer.service;

public class TransferException extends Exception{

    private final Reason reason;

    public TransferException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public TransferException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        // deterministic business errors
        INSUFFICIENT_FUNDS,
        ACCOUNT_NOT_FOUND,
        FX_RATE_MISSING,
        INVALID_CURRENCY,
        INVALID_FEE_AMOUNT,
        SELF_TRANSFER, INVALID_TRANSFER_RECORD,
    }
}