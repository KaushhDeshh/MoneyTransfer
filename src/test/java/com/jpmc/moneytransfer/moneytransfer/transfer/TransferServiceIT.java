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
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.util.Pair;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

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

    @AfterEach
    void tearDown() {
        transferRepository.deleteAll();
        accountRepository.deleteAll();
        currencyRepository.deleteAll();
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

        Transfer transfer = transferRepository.findById(transferId).orElseThrow();
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

    /**
     *   Cuncurrency test
     * */
    @Test
    void concurrencyTest() throws Exception {
        List<Account> accounts = createAccounts(10);
        BigDecimal total = BigDecimal.ZERO;

        // Store account IDs for later reload
        List<Long> accountIds = accounts.stream()
                .map(Account::getId)
                .toList();

        // Get initial balances
        for (Account a : accounts) {
            total = total.add(a.getBalance());
        }

        // Run concurrent transfers
        BigDecimal fees = runRandomConcurrentTransfers(accounts);

        // Reload the same accounts
        List<Account> updatedAccounts = accountIds.stream()
                .map(id -> accountRepository.findById(id).orElseThrow())
                .toList();

        BigDecimal updatedTotal = updatedAccounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Assertions.assertEquals(total, updatedTotal.add(fees), "Mismatch: balances + fees should equal original total");

    }

    public List<Account> createAccounts(int numAccounts) {
        Currency usd = currencyRepository.findById("USD").orElseThrow();

        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < numAccounts; i++) {
            accounts.add(new Account("Account" + i, usd, new BigDecimal("1000.00")));
        }


        accountRepository.saveAll(accounts);

        return accounts.stream()
                .map(a -> accountRepository.findById(a.getId()).orElseThrow())
                .toList();
    }

    public Pair<Account, Account> pickRandomSenderAndReceiver(List<Account> accounts) {
        if (accounts.size() < 2) {
            throw new IllegalArgumentException("Need at least two accounts to pick sender and receiver");
        }

        Random rand = new Random();
        Account sender, receiver;

        do {
            sender = accounts.get(rand.nextInt(accounts.size()));
            receiver = accounts.get(rand.nextInt(accounts.size()));
        } while (sender.getId().equals(receiver.getId()));

        return Pair.of(sender, receiver);
    }


    /**
     *  Runs a random concurrent transfer on the given accounts keeps track of the total fees.
     * */
    public BigDecimal runRandomConcurrentTransfers(List<Account> accounts) throws InterruptedException {
        int numTransfers = 100;
        BigDecimal minAmount = new BigDecimal("10.00");
        BigDecimal maxAmount = new BigDecimal("200.00");

        AtomicReference<BigDecimal> fees = new AtomicReference<>(BigDecimal.ZERO);

        ExecutorService executor = Executors.newFixedThreadPool(numTransfers);
        CountDownLatch latch = new CountDownLatch(numTransfers);

        for (int i = 0; i < numTransfers; i++) {
            executor.submit(() -> {
                try {
                    Pair<Account, Account> pair = pickRandomSenderAndReceiver(accounts);

                    TransferRequestDTO dto = new TransferRequestDTO();
                    dto.setSenderAccountId(pair.getFirst().getId());
                    dto.setReceiverAccountId(pair.getSecond().getId());
                    dto.setAmount(randomAmount(minAmount, maxAmount));
                    dto.setCurrency("USD");

                    Long id = transferService.transferMoney(dto);

                    Transfer transfer = transferRepository.findById(id).orElseThrow();
                    if(transfer.getState() == TransferState.COMPLETED) {
                        fees.getAndUpdate(t -> t.add(transfer.getFeeApplied()));
                    }


                } catch (Exception e) {
                    System.out.println("Transfer failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        return fees.get();
    }

    private BigDecimal randomAmount(BigDecimal min, BigDecimal max) {
        BigDecimal range = max.subtract(min);
        BigDecimal random = BigDecimal.valueOf(Math.random());
        return commonHelper.round(min.add(range.multiply(random)));
    }

}
