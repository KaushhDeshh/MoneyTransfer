package com.jpmc.moneytransfer.moneytransfer;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Common Helper class for Money Transfer Application
 */
@Component
public class CommonHelper {

    public static final int MONEY_SCALE = 4;

    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Rounds the given BigDecimal to the standard money scale using HALF_UP.
     */
    public BigDecimal round(BigDecimal amount) {
        if (amount == null) return null;
        return amount.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    /**
     * Utility method to divide two BigDecimals with standard rounding.
     */
    public BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Invalid division input");
        }
        return numerator.divide(denominator, MONEY_SCALE, ROUNDING_MODE);
    }

    /**
     * Utility method to multiply and round the result.
     */
    public BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return null;
        return a.multiply(b).setScale(MONEY_SCALE, ROUNDING_MODE);
    }

}
