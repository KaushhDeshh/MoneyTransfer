package com.jpmc.moneytransfer.moneytransfer.transfer;

import com.jpmc.moneytransfer.moneytransfer.CommonHelper;
import com.jpmc.moneytransfer.moneytransfer.account.model.Account;
import com.jpmc.moneytransfer.moneytransfer.account.model.Currency;
import com.jpmc.moneytransfer.moneytransfer.account.repository.AccountRepository;
import com.jpmc.moneytransfer.moneytransfer.account.repository.CurrencyRepository;
import com.jpmc.moneytransfer.moneytransfer.transfer.model.Transfer;
import com.jpmc.moneytransfer.moneytransfer.transfer.model.TransferRequestDTO;
import com.jpmc.moneytransfer.moneytransfer.transfer.model.TransferState;
import com.jpmc.moneytransfer.moneytransfer.transfer.repository.TransferRepository;
import com.jpmc.moneytransfer.moneytransfer.transfer.service.FXConversionService;
import com.jpmc.moneytransfer.moneytransfer.transfer.service.TransferException;
import com.jpmc.moneytransfer.moneytransfer.transfer.service.TransferService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY) // Use H2
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransferServiceIT {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private TransferRepository transferRepository;
    @Autowired
    private CommonHelper commonHelper;
    @Autowired
    private FXConversionService fxConversionService;

    private Currency usd;
    private Currency eur;
    private Currency chf;

    @BeforeEach
    void setup() {
        // Create sample currency
        usd = new Currency("USD", "United States Dollar");
        eur = new Currency("EUR", "Euro");
        chf = new Currency("CHF", "Franc");
        currencyRepository.saveAll(List.of(usd, eur, chf));
        // Accounts
        Account sender = new Account("Alice", usd, new BigDecimal("1000.00"));
        Account receiver = new Account("Bob", usd, new BigDecimal("500.00"));
        accountRepository.saveAll(List.of(sender, receiver));
        fxConversionService.addRate(usd, eur, new BigDecimal("0.80"));
    }

    @Test
    void testHappyPathTransfer() throws Exception {
        // Get accounts
        // Build transfer request
        TransferRequestDTO dto = new TransferRequestDTO();
        dto.setSenderAccountId(1L);
        dto.setReceiverAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setCurrency("USD");

        // Call service
        Long transferId = transferService.transferMoney(dto);

        // Verify results
        Transfer transfer = transferRepository.findById(transferId).orElseThrow();
        Assertions.assertEquals(TransferState.COMPLETED, transfer.getState());
        Assertions.assertEquals(commonHelper.round(BigDecimal.valueOf(899.0)), transfer.getFromAccount().getBalance());
        Assertions.assertEquals(commonHelper.round(BigDecimal.valueOf(600.0)), transfer.getToAccount().getBalance());
    }


    @Test
    void testFxHappyPathTransfer() throws Exception {
        // Create FX accounts
        Account fxSender = new Account("FXAlice", usd, new BigDecimal("1000.00"));
        Account fxReceiver = new Account("FXBob", eur, new BigDecimal("100.00"));
        accountRepository.saveAll(List.of(fxSender, fxReceiver));

        // Build request
        TransferRequestDTO dto = new TransferRequestDTO();
        dto.setSenderAccountId(fxSender.getId());
        dto.setReceiverAccountId(fxReceiver.getId());
        dto.setAmount(new BigDecimal("100.00"));
        dto.setCurrency("USD");

        Long transferId = transferService.transferMoney(dto);

        Transfer transfer = transferRepository.findWithAccountsById(transferId).orElseThrow();

        Assertions.assertEquals(TransferState.COMPLETED, transfer.getState());
        Assertions.assertEquals(commonHelper.round(BigDecimal.valueOf(899.0)),transfer.getFromAccount().getBalance());
        Assertions.assertEquals(commonHelper.round(BigDecimal.valueOf(180.0)), transfer.getToAccount().getBalance());
        Assertions.assertEquals(0, transfer.getFxRate().compareTo(new BigDecimal("0.80")));
    }

    @Test
    void testMissingFxRateShouldThrow() {
        // Create accounts without FX rate setup (USD -> JPY is missing)
        Account fxSender = new Account("FXNoRateSender", usd, new BigDecimal("500.00"));
        Account fxReceiver = new Account("FXNoRateReceiver", chf, new BigDecimal("10000.00"));
        accountRepository.saveAll(List.of(fxSender, fxReceiver));

        TransferRequestDTO dto = new TransferRequestDTO();
        dto.setSenderAccountId(fxSender.getId());
        dto.setReceiverAccountId(fxReceiver.getId());
        dto.setAmount(new BigDecimal("100.00"));
        dto.setCurrency("USD");

        TransferException ex = Assertions.assertThrows(
                TransferException.class,
                () -> transferService.transferMoney(dto)
        );

        Assertions.assertEquals(TransferException.Reason.FX_RATE_MISSING, ex.getReason());
    }

}
