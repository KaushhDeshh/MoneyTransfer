package com.jpmc.moneytransfer.moneytransfer.transfer.model;

/**
 * Lifecycle of a transfer. Extend with more states as needed.
 * */
public enum TransferState {
    PROCESSING,
    COMPLETED,
    FAILED
}