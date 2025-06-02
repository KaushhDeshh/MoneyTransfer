package com.jpmc.moneytransfer.moneytransfer.transfer.model;

import com.jpmc.moneytransfer.moneytransfer.account.model.Account;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfer")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(name = "from_account_id", insertable = false, updatable = false)
    private Long fromAccountId;

    @Column(name = "to_account_id", insertable = false, updatable = false)
    private Long toAccountId;


    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "fee_applied", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeApplied;

    @Column(name = "fx_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal fxRate;

    @Column(name = "converted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal convertedAmount;

    @Column(name = "currency_from", nullable = false, length = 3)
    private String currencyFrom;

    @Column(name = "currency_to", nullable = false, length = 3)
    private String currencyTo;

    @Column(name = "transfer_time", nullable = false)
    private LocalDateTime transferTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private TransferState state;

    @PrePersist
    public void onCreate() {
        this.transferTime = LocalDateTime.now();
    }

    protected Transfer() {}

    public Transfer(Account from, Account to, BigDecimal amount,
                    BigDecimal fee, BigDecimal fxRate, BigDecimal convertedAmount,
                    String currencyFrom, String currencyTo) {
        this.fromAccount = from;
        this.toAccount = to;
        this.amount = amount;
        this.feeApplied = fee;
        this.fxRate = fxRate;
        this.convertedAmount = convertedAmount;
        this.currencyFrom = currencyFrom;
        this.currencyTo = currencyTo;
    }

    public Long getId() {
        return id;
    }



    public Account getFromAccount() {
        return fromAccount;
    }

    public void setFromAccount(Account fromAccount) {
        this.fromAccount = fromAccount;
    }

    public Account getToAccount() {
        return toAccount;
    }

    public void setToAccount(Account toAccount) {
        this.toAccount = toAccount;
    }

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public void setFromAccountId(Long fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public void setToAccountId(Long toAccountId) {
        this.toAccountId = toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getFeeApplied() {
        return feeApplied;
    }

    public void setFeeApplied(BigDecimal feeApplied) {
        this.feeApplied = feeApplied;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public void setFxRate(BigDecimal fxRate) {
        this.fxRate = fxRate;
    }

    public BigDecimal getConvertedAmount() {
        return convertedAmount;
    }

    public void setConvertedAmount(BigDecimal convertedAmount) {
        this.convertedAmount = convertedAmount;
    }

    public String getCurrencyFrom() {
        return currencyFrom;
    }

    public void setCurrencyFrom(String currencyFrom) {
        this.currencyFrom = currencyFrom;
    }

    public String getCurrencyTo() {
        return currencyTo;
    }

    public void setCurrencyTo(String currencyTo) {
        this.currencyTo = currencyTo;
    }

    public LocalDateTime getTransferTime() {
        return transferTime;
    }

    public void setTransferTime(LocalDateTime transferTime) {
        this.transferTime = transferTime;
    }

    public TransferState getState() {
        return state;
    }

    public void setState(TransferState state) {
        this.state = state;
    }


}
