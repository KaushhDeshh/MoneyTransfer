package com.jpmc.moneytransfer.moneytransfer;

import com.jpmc.moneytransfer.moneytransfer.account.model.Account;
import com.jpmc.moneytransfer.moneytransfer.account.model.Currency;
import com.jpmc.moneytransfer.moneytransfer.account.repository.AccountRepository;
import com.jpmc.moneytransfer.moneytransfer.account.repository.CurrencyRepository;
import com.jpmc.moneytransfer.moneytransfer.transfer.model.TransferRequestDTO;
import com.jpmc.moneytransfer.moneytransfer.transfer.service.TransferService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 *   Temporary Test Controller for testing the application (have not implemented any security, validation or exception handling)
 * */
@RestController
@RequestMapping("/test")
public class TempTestController {
    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private CommonHelper commonHelper;

    private static final Logger log = LoggerFactory.getLogger(TempTestController.class);

    @Autowired
    private TransferService transferService;

    /**
     *  Create a currency and return the newly created currency object
     */
    @PostMapping("/currency")
    public ResponseEntity<Currency> createCurrency(@RequestBody Map<String, String> request) {
        String code = request.get("code").toUpperCase();
        String name = request.get("name");
        log.info("Creating currency with code={} name={}", code, name);

        Currency currency = new Currency(code, name);
        Currency saved = currencyRepository.save(currency);

        log.info("Currency created: {}", saved.getCode());
        return ResponseEntity.ok(saved);
    }

    /**
     *  Create an account and return the newly created account object
     */
    @PostMapping("/account")
    @Transactional
    public ResponseEntity<Account> createAccount(@RequestBody CreateAccountRequest request) {

        log.info("Received account creation request: name={}, currencyCode={}, initialBalance={}",
                request.name, request.currencyCode, request.initialBalance);

        if (request.initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }

        BigDecimal rounded = commonHelper.round(request.initialBalance);

        Currency currency = currencyRepository.findByCode(request.currencyCode)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported currency: " + request.currencyCode));

        Account account = new Account(request.name, currency, rounded);
        Account saved = accountRepository.save(account);

        log.info("Account created: id={}, name={}", saved.getId(), saved.getName());

        return ResponseEntity.ok(saved);
    }

    public static class CreateAccountRequest {
        public String name;
        public String currencyCode;
        public BigDecimal initialBalance;
    }


    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transfer(
            @Valid @RequestBody TransferRequestDTO dto) {

        log.info("Transfer request: {}", dto);
        Long id = -1L;
        try {
            id = transferService.transferMoney(dto);
        } catch (Exception e) {
            ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        return ResponseEntity.ok(
                Map.of("transferId", id,
                        "status", "ACCEPTED"));
    }


}