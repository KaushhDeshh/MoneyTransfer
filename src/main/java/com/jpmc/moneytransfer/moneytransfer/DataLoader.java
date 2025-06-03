package com.jpmc.moneytransfer.moneytransfer;

import com.jpmc.moneytransfer.moneytransfer.account.model.Currency;
import com.jpmc.moneytransfer.moneytransfer.account.repository.CurrencyRepository;
import com.jpmc.moneytransfer.moneytransfer.transfer.model.TransferPolicy;
import com.jpmc.moneytransfer.moneytransfer.transfer.repository.TransferPolicyRepository;
import com.jpmc.moneytransfer.moneytransfer.transfer.service.FXConversionService;
import com.jpmc.moneytransfer.moneytransfer.transfer.service.FeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

import static com.jpmc.moneytransfer.moneytransfer.transfer.service.FeeService.TRANSFER_FEE_POLICY_KEY;

/**
 *  Data Loader for loading initial data into the database
 *  This is a temporary solution for demo purpose
 *  */
@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    CurrencyRepository currencyRepository;

    @Autowired
    TransferPolicyRepository transferPolicyRepository;

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);
    @Autowired
    private FXConversionService fXConversionService;

    @Override
    public void run(String... args) {
        log.info("Loading data...");
        loadCurrencies();
        loadFeePolicy();
        loadFXRates();
        log.info("Data loaded successfully.");
    }

    private void loadCurrencies() {

            List<Currency> currencies = List.of(
                    new Currency("USD", "US Dollar"),
                    new Currency("JPY", "Japanese Yen"),
                    new Currency("AUD", "Australian Dollar")
            );
            currencyRepository.saveAll(currencies);
    }

    private void loadFeePolicy() {
        if (transferPolicyRepository.existsById("TRANSFER_FEE")) {
            log.info("TRANSFER_FEE policy already exists. Skipping.");
            return;
        }

        TransferPolicy policy = new TransferPolicy(
                TRANSFER_FEE_POLICY_KEY,
                BigDecimal.valueOf(0.01),
                true
        );



        transferPolicyRepository.save(policy);
        log.info("TRANSFER_FEE policy created with value = {}", policy.getValue());
    }


    /**
     *  Load FX rates
     * */
    private void loadFXRates() {
        Currency usd = currencyRepository.findById("USD")
                .orElseThrow(() -> new IllegalStateException("USD missing"));
        Currency aud = currencyRepository.findById("AUD")
                .orElseThrow(() -> new IllegalStateException("AUD missing"));
        Currency jpy = currencyRepository.findById("JPY")
                .orElseThrow(() -> new IllegalStateException("JPY missing"));

        fXConversionService.addRate(usd, aud, BigDecimal.valueOf(2.0));
        fXConversionService.addRate(aud, usd, BigDecimal.valueOf(0.50));

        fXConversionService.addRate(usd, jpy, BigDecimal.valueOf(150));
        fXConversionService.addRate(jpy, usd, BigDecimal.valueOf(0.00667));

        fXConversionService.addRate(aud, jpy, BigDecimal.valueOf(75));
        fXConversionService.addRate(jpy, aud, BigDecimal.valueOf(0.0133));

        log.info("FX rates seeded.");
    }

}
