package com.jpmc.moneytransfer.moneytransfer.transfer.service;

/*
 * FX Conversion Service
 * */

import com.jpmc.moneytransfer.moneytransfer.account.model.Currency;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FX Conversion Service talks to an external FX provider to convert currency (currently a mock HashMap)
 * */
@Service
public class FXConversionService {

    //FX rates for each currency pair
    private final Map<Currency, Map<Currency, BigDecimal>> fxRates = new ConcurrentHashMap<>();


    public void addRate(Currency from, Currency to, BigDecimal rate) {
        fxRates.computeIfAbsent(from, k -> new ConcurrentHashMap<>()).put(to, rate);
    }

    public BigDecimal getRate(Currency from, Currency to) throws TransferException {
        BigDecimal rate =
                fxRates.getOrDefault(from, Map.of())
                        .get(to);

        if (rate == null) {
            throw new TransferException(
                    TransferException.Reason.FX_RATE_MISSING,
                    "FX rate not available for " + from + " → " + to);
        }
        return rate;
    }
}
