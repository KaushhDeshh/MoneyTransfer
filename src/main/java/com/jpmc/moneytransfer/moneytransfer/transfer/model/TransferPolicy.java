package com.jpmc.moneytransfer.moneytransfer.transfer.model;

import com.jpmc.moneytransfer.moneytransfer.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

/*
 * Transfer Policy Entity
 * */

@Entity
@Table(name = "transfer_policy")
public class TransferPolicy  extends BaseEntity {

    @Id
    @Column(name = "policy_name", nullable = false, length = 100)
    private String policyName;

    @Column(name = "policy_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal value;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected TransferPolicy() {
    }

    public TransferPolicy(String policyName, BigDecimal value, boolean enabled) {
        this.policyName = policyName;
        this.value = value;
        this.enabled = enabled;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
