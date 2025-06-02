package com.jpmc.moneytransfer.moneytransfer.transfer.model;


import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class TransferRequestDTO {

    @NotNull(message = "Sender account id is required")
    @Positive(message = "Sender account id must be positive")
    private Long senderAccountId;

    @NotNull(message = "Receiver account id is required")
    @Positive(message = "Receiver account id must be positive")
    private Long receiverAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", inclusive = true,
            message = "Amount must be at least 0.01")
    @Digits(integer = 15, fraction = 4,
            message = "Amount must have max 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    /**
     * currency code, e.g. “USD”, “JPY”.
     */
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$",
            message = "Currency must be a valid 3-letter ISO-4217 code in upper-case")
    private String currency;


    public TransferRequestDTO() { /* for Jackson / Bean Validation */ }

    public TransferRequestDTO(Long senderAccountId, Long receiverAccountId,  BigDecimal amount, String currency) {
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.amount = amount;
        this.currency = currency;
    }

    public Long getSenderAccountId() {
        return senderAccountId;
    }

    public void setSenderAccountId(Long senderAccountId) {
        this.senderAccountId = senderAccountId;
    }

    public Long getReceiverAccountId() {
        return receiverAccountId;
    }

    public void setReceiverAccountId(Long receiverAccountId) {
        this.receiverAccountId = receiverAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
