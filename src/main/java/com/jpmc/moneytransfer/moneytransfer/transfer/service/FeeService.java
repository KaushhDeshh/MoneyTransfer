package com.jpmc.moneytransfer.moneytransfer.transfer.service;


import com.jpmc.moneytransfer.moneytransfer.CommonHelper;
import com.jpmc.moneytransfer.moneytransfer.transfer.model.TransferPolicy;
import com.jpmc.moneytransfer.moneytransfer.transfer.repository.TransferPolicyRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Fee Service to calculate the fee for transfer and store the fee as cache
 */
@Service
public class FeeService {

    public static final String TRANSFER_FEE_POLICY_KEY = "TRANSFER_FEE";

    private static final Logger log = LoggerFactory.getLogger(FeeService.class);

    @Autowired
    private TransferPolicyRepository transferPolicyRepository;

    private TransferPolicy transferFeePolicy;
    @Autowired
    private CommonHelper commonHelper;

    @PostConstruct
    public void init() {
        try {
            refreshCache();
        } catch (IllegalStateException e) {
            log.error("Error loading transfer fee policy", e);
        }
    }

    public void refreshCache() {

        this.transferFeePolicy = transferPolicyRepository.findById(TRANSFER_FEE_POLICY_KEY)
                .orElseThrow(() -> new IllegalStateException("TRANSFER_FEE policy not found in DB"));

        log.info("Loaded transfer fee policy: {} = {}",
                transferFeePolicy.getPolicyName(),
                transferFeePolicy.getValue());
    }

    public TransferPolicy getTransferFeePolicy() {
        return transferFeePolicy;
    }

    public BigDecimal calculateFee(BigDecimal amount) {

        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }

        if (transferFeePolicy == null || !transferFeePolicy.isEnabled()) {
            return BigDecimal.ZERO;
        }

         return commonHelper.multiply(amount, transferFeePolicy.getValue());

    }


}
