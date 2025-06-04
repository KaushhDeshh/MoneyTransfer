package com.jpmc.moneytransfer.moneytransfer.transfer.service;

import com.jpmc.moneytransfer.moneytransfer.CommonHelper;
import com.jpmc.moneytransfer.moneytransfer.account.model.Account;
import com.jpmc.moneytransfer.moneytransfer.account.model.Currency;
import com.jpmc.moneytransfer.moneytransfer.account.repository.AccountRepository;
import com.jpmc.moneytransfer.moneytransfer.account.repository.CurrencyRepository;
import com.jpmc.moneytransfer.moneytransfer.transfer.model.Transfer;
import com.jpmc.moneytransfer.moneytransfer.transfer.model.TransferRequestDTO;
import com.jpmc.moneytransfer.moneytransfer.transfer.model.TransferState;
import com.jpmc.moneytransfer.moneytransfer.transfer.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static com.jpmc.moneytransfer.moneytransfer.CommonHelper.MAX_DB_VALUE;

/**
 * Service for Transferring Money from one account to another
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private FeeService feeService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FXConversionService fxConversionService;

    @Autowired
    private CommonHelper commonHelper;

    @Autowired
    private ApplicationContext applicationContext;



    /**
     *  Service Entry Point
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
     public Long transferMoney(TransferRequestDTO transferRequestDTO) throws TransferException, TransferRuntimeException {
         Transfer transfer = createAndSaveTransfer(transferRequestDTO);
         log.info("Transfer created : {}", transfer.getId());
         try {
             log.info("Performing transfer {}", transfer.getId());
             checkSelfTransfer(transfer);

             //Springs Invokation Issue with Transactional scopes
             performTransfer(transfer);

             log.info("Transfer completed {}", transfer.getId());
             return transfer.getId();
         } catch (TransferException | TransferRuntimeException ex) {

             log.error("Transfer failed {}", transfer.getId(), ex);
             updateTransferRecordAsFailed(transfer);
             throw ex;
         }

     }


     protected void checkSelfTransfer(Transfer transfer) throws TransferException {
         if(transfer.getFromAccountIdRaw().equals(transfer.getToAccountIdRaw())){
             throw new TransferException(
                     TransferException.Reason.SELF_TRANSFER,
                     "Self transfer not allowed");
         }
     }

    /**
     *  Opens a DB Transaction and saves a Transfer Record
     * */

    protected Transfer createAndSaveTransfer(TransferRequestDTO transferRequestDTO) throws TransferException ,TransferRuntimeException{

        Transfer transfer = new Transfer(
                transferRequestDTO.getSenderAccountId(),
                transferRequestDTO.getReceiverAccountId(),
                transferRequestDTO.getAmount(),
                TransferState.PROCESSING);

            Currency currency = getCurrencyFromDTO(transferRequestDTO.getCurrency());
            transfer.setCurrency(currency);
            transfer = transferRepository.save(transfer);

            log.info("Transfer transfer {} created (from={} to={} amt={} {})",
                    transfer.getId(),
                    transfer.getFromAccountIdRaw(), transfer.getToAccountIdRaw(),
                    transfer.getAmount(), transfer.getCurrencyFrom());

            return transfer;
    }

    /**
     *  Grabs the Currency from the DTO and returns it. Throws an exception if the currency is not supported.
     * @param  currencyCode
     * @return Currency
     * */
    protected Currency getCurrencyFromDTO(String currencyCode) throws TransferException {
        return currencyRepository.findByCode(currencyCode)
                .orElseThrow(() -> new TransferException(
                        TransferException.Reason.INVALID_CURRENCY,
                        "Unsupported currency code: " + currencyCode));
    }



    /**
     * Calculates and applies the transfer fee.
     */
    protected void processTransferFee(Transfer transfer) {
        try {
            BigDecimal fee = feeService.calculateFee(transfer.getAmount());
            //if fee is negative
            if (fee == null || fee.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Fee must be non-null and non-negative");
            }

            transfer.setFeeApplied(fee);
        } catch (Exception e) {
            throw new TransferRuntimeException(
                    TransferRuntimeException.Reason.FEE_CALCULATION_FAILED,
                    "Failed to calculate transfer fee", e);
        }
    }


    /**
     * Marks the transfer record as FAILED and persists the update.
     */
    @Transactional
    protected void updateTransferRecordAsFailed(Transfer transfer) {
        transfer.setState(TransferState.FAILED);
        transferRepository.save(transfer);
    }


    /**
     *  Preforms the DB Transaction and marks Transfer as Completed.
     * */

    protected Transfer performTransfer(Transfer transfer) throws TransferException {

        if(transfer == null){
           throw new TransferException(
                    TransferException.Reason.INVALID_TRANSFER_RECORD,
                    "Invalid transfer record");
        }

        // Persists the transfer record
        transferRepository.save(transfer);

        //calculate transfer fee (don't need account locks to do this)
        processTransferFee(transfer);

        // Locking Accounts
        attachLockedAccountsOrdered(transfer);

        //Check if currency matches sender's account currency
        validSenderCurrencyCheck(transfer);

        //calculate and check
        computeAmounts(transfer);
        sufficientBalanceCheck(transfer.getFromAccount(),
                transfer.getToAccount(),
                transfer.getDebitAmount(),
                transfer.getCreditAmount());

        //perform actual debit and credit operations
        preformDebitAndCredit(transfer);
        transfer.setState(TransferState.COMPLETED);

        log.info("Transfer {} completed", transfer.getId());


        return transfer;
    }

    /**
     *  Performs the actual debit and credit operations.
     * */
    protected void preformDebitAndCredit(Transfer transfer) throws TransferRuntimeException {

        Account sender = transfer.getFromAccount();
        Account receiver = transfer.getToAccount();

        if (sender == null || receiver == null) {
            throw new TransferRuntimeException(
                    TransferRuntimeException.Reason.INVALID_ARGUMENT,
                    "Sender or receiver account is null");
        }

        try {
            sender.debit(transfer.getDebitAmount());
            receiver.credit(transfer.getCreditAmount());

        } catch (Exception e) {
            throw new TransferRuntimeException(
                    TransferRuntimeException.Reason.UNKNOWN_ERROR,
                    "Failed to debit or credit accounts", e);
        }
    }


    /**
     *   Checks if the credit and debit amount are valid
     * */
    protected void sufficientBalanceCheck(Account sender, Account receiver, BigDecimal debitAmount, BigDecimal creditAmount) throws TransferException {
        // Check sender balance
        if (sender.getBalance().compareTo(debitAmount) < 0) {
            throw new TransferException(
                    TransferException.Reason.INSUFFICIENT_FUNDS,
                    "Insufficient funds for this transaction");
        }

        // Check if receiver's new balance exceeds DB precision
        BigDecimal newBalance = receiver.getBalance().add(creditAmount);
        if (newBalance.compareTo(MAX_DB_VALUE) > 0) {
            throw new TransferException(
                    TransferException.Reason.INSUFFICIENT_FUNDS,
                    "Receiver balance exceeds database precision limit");
        }
    }


    /**
     *  Checks if the given currency matches the sender's account currency.
     * */

    protected void validSenderCurrencyCheck(Transfer transfer) throws TransferException {
        if(!transfer.getFromAccount().getCurrency().equals(transfer.getCurrency())){
            throw new TransferException(
                    TransferException.Reason.INVALID_CURRENCY,
                    "Currency mismatch from DTO and Sender's Account");
        }
    }

    /**
     *  Attaches the accounts to the transfer record.
     *  The accounts are attached in ascending order to avoid deadlocks.
     *  This is where the accounts are locked.
     * */
    protected void attachLockedAccountsOrdered(Transfer transfer) throws TransferException {
        log.info("Locking Accounts {} and {}", transfer.getFromAccountIdRaw(), transfer.getToAccountIdRaw());
        Long senderId = transfer.getFromAccountIdRaw();
        Long receiverId = transfer.getToAccountIdRaw();

        Account sender, receiver;
        if (senderId < receiverId) {
            sender = getAccountOrThrow(senderId, "Sender");
            receiver = getAccountOrThrow(receiverId, "Receiver");
        } else {
            receiver = getAccountOrThrow(receiverId, "Receiver");
            sender = getAccountOrThrow(senderId, "Sender");
        }

        validateAccountsHaveCurrency(sender, receiver);
        transfer.setFromAccount(sender);
        transfer.setToAccount(receiver);
        transfer.setCurrencyFrom(sender.getCurrency());
        transfer.setCurrencyTo(receiver.getCurrency());

        log.info("Accounts locked: from={} to={}", senderId, receiverId);

    }

    /**
     *  Locks Given Account
     * */
    private Account getAccountOrThrow(Long id, String role) throws TransferException {
        return accountRepository.findById(id)
                .orElseThrow(() -> new TransferException(
                        TransferException.Reason.ACCOUNT_NOT_FOUND,
                        role + " account not found: " + id));
    }





    /**
     *  Computing credit and debit can be tightly coupled so I put them together
     * */
    protected void computeAmounts(Transfer transfer) throws TransferException {
        BigDecimal debit = transfer.getAmount().add(transfer.getFeeApplied());
        transfer.setDebitAmount(debit);
        log.info("Debit amount for transfer {}: {}", transfer.getId(), transfer.getDebitAmount());

        if (transfer.getCurrencyFrom().equals(transfer.getCurrencyTo())) {
            transfer.setCreditAmount(transfer.getAmount());
        } else {
            BigDecimal fxRate = fxConversionService.getRate(transfer.getCurrencyFrom(), transfer.getCurrencyTo());
            BigDecimal converted = commonHelper.multiply(fxRate, transfer.getAmount());
            transfer.setFxRate(fxRate);
            transfer.setCreditAmount(converted);

            log.info("Converted {} {} → {} {} @ {}", transfer.getAmount(),
                    transfer.getCurrencyFrom().getCode(), converted,
                    transfer.getCurrencyTo().getCode(), fxRate);
        }
    }


    /**
     *  Validates if accounts have currency
     * */
    protected void validateAccountsHaveCurrency(Account from, Account to) {
        if (from.getCurrency() == null || to.getCurrency() == null) {
            throw new TransferRuntimeException(
                    TransferRuntimeException.Reason.INVALID_ACCOUNT_STATE,
                    "One or both accounts have no assigned currency");
        }
    }
}

