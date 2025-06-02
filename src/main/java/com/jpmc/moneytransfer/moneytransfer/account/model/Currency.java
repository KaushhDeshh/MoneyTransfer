package com.jpmc.moneytransfer.moneytransfer.account.model;

import com.jpmc.moneytransfer.moneytransfer.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 *  Currency Entity
 * */
@Entity
@Table(name = "currency")
public class Currency extends BaseEntity {

    @Id
    @Column(name = "currency_code",length = 3)
    private String code;

    @Column(name = "currency_name",nullable = false, length = 50)
    private String name;

    public Currency() {
    }

    public Currency(String code, String name) {
        this.code = code;
        this.name = name;
    }


    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Currency)) return false;
        Currency other = (Currency) o;
        return Objects.equals(this.code, other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
}
