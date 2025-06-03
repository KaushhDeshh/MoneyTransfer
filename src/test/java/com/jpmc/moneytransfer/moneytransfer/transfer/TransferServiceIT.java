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
import com.jpmc.moneytransfer.moneytransfer.transfer.service.TransferService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY) // Use H2
@Transactional // Each test rolls back automatically
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

    @BeforeEach
    void setup() {
        // Create sample currency
        Currency usd = new Currency("USD", "United States Dollar");
        currencyRepository.save(usd);

        // Create accounts
        Account sender = new Account("Alice", usd, new BigDecimal("1000.00"));
        Account receiver = new Account("Bob", usd, new BigDecimal("500.00"));

        accountRepository.save(sender);
        accountRepository.save(receiver);
    }

    @Test
    void testHappyPathTransfer() throws Exception {
        // Get accounts
        Account sender = accountRepository.findAll().get(0);
        Account receiver = accountRepository.findAll().get(1);

        // Build transfer request
        TransferRequestDTO dto = new TransferRequestDTO();
        dto.setSenderAccountId(sender.getId());
        dto.setReceiverAccountId(receiver.getId());
        dto.setAmount(new BigDecimal("100.00"));
        dto.setCurrency("USD");

        // Call service
        Long transferId = transferService.TransferMoney(dto);

        // Verify results
        Transfer transfer = transferRepository.findById(transferId).orElseThrow();
        Assertions.assertEquals(TransferState.COMPLETED, transfer.getState());
        Assertions.assertEquals(commonHelper.round(BigDecimal.valueOf(899.0)), transfer.getFromAccount().getBalance());
        Assertions.assertEquals(commonHelper.round(BigDecimal.valueOf(600.0)), transfer.getToAccount().getBalance());
    }
}
