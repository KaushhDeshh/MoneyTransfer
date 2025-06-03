package com.jpmc.moneytransfer.moneytransfer.account.model;

import com.jpmc.moneytransfer.moneytransfer.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

import static com.jpmc.moneytransfer.moneytransfer.CommonHelper.MONEY_SCALE;
import static com.jpmc.moneytransfer.moneytransfer.CommonHelper.ROUNDING_MODE;

/**
 *  Account Entity
 * */
@Entity
@Table(name = "account")
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "currency_code", referencedColumnName = "currency_code")
    private Currency currency;

    // used a scale of 4 to avoid rounding errors as much as possible
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal balance;


    public Account(String name, Currency currency, BigDecimal balance) {
        this.name = name;
        this.currency = currency;
        this.balance = balance;
    }

    public Account() {
    }


    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }


    public void debit(BigDecimal amount) {

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }

        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds for debit: required " + amount + ", available " + balance);
        }

        this.balance = balance.subtract(amount).setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    public void credit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }

        this.balance = balance.add(amount).setScale(MONEY_SCALE, ROUNDING_MODE);
    }
}