package com.jpmc.moneytransfer.moneytransfer.transfer.model;

import com.jpmc.moneytransfer.moneytransfer.account.model.Account;
import com.jpmc.moneytransfer.moneytransfer.account.model.Currency;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 *  Transfer Entity keeps record of the Transfer Request
 * */
@Entity
@Table(name = "transfer")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id")
    private Long id;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(name = "from_account_id_raw", updatable = false)
    private Long fromAccountIdRaw;

    @Column(name = "to_account_id_raw",  updatable = false)
    private Long toAccountIdRaw;


    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "fee_applied", nullable = true, precision = 19, scale = 4)
    private BigDecimal feeApplied;

    @Column(name = "fx_rate", nullable = true, precision = 19, scale = 6)
    private BigDecimal fxRate;

    @Column(name = "debit_Amount", nullable = true, precision = 19, scale = 4)
    private BigDecimal debitAmount;


    @Column(name = "credit_Amount", nullable = true, precision = 19, scale = 4)
    private BigDecimal creditAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "currency", referencedColumnName = "currency_code")
    private Currency currency;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "currency_from", referencedColumnName = "currency_code")
    private Currency currencyFrom;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "currency_to", referencedColumnName = "currency_code")
    private Currency currencyTo;

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


    public Transfer(Long fromAccountId,
                    Long toAccountId,
                    BigDecimal amount,
                    TransferState state){
        this.fromAccountIdRaw = fromAccountId;
        this.toAccountIdRaw = toAccountId;
        this.amount = amount;
        this.state = state;
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

    public Long getFromAccountIdRaw() {
        return fromAccountIdRaw;
    }

    public void setFromAccountIdRaw(Long fromAccountId) {
        this.fromAccountIdRaw = fromAccountId;
    }

    public Long getToAccountIdRaw() {
        return toAccountIdRaw;
    }

    public void setToAccountIdRaw(Long toAccountId) {
        this.toAccountIdRaw = toAccountId;
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

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public void setDebitAmount(BigDecimal convertedAmount) {
        this.debitAmount = convertedAmount;
    }

    public Currency getCurrencyFrom() {
        return currencyFrom;
    }

    public void setCurrencyFrom(Currency currencyFrom) {
        this.currencyFrom = currencyFrom;
    }

    public Currency getCurrencyTo() {
        return currencyTo;
    }

    public void setCurrencyTo(Currency currencyTo) {
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


    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public void setCreditAmount(BigDecimal creditAmount) {
        this.creditAmount = creditAmount;
    }
}
