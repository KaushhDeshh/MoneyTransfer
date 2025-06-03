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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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



    /**
     *  Service Entry Point
     */
     public Long TransferMoney(TransferRequestDTO transferRequestDTO) throws TransferException, TransferRuntimeException {
         Transfer transfer = createAndSaveTransfer(transferRequestDTO);
         log.info("Transfer created : {}", transfer.getId());
         try {
             log.info("Performing transfer {}", transfer.getId());
             checkSelfTransfer(transfer);
             performTransfer(transfer);
             log.info("Transfer completed {}", transfer.getId());
             return transfer.getId();
         } catch (TransferException | TransferRuntimeException ex) {
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

    @Transactional
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        computeDebit(transfer);
        inSufficientBalanceCheck(transfer.getFromAccount(), transfer.getDebitAmount());

        log.info("Debit amount for transfer {}: {}", transfer.getId(), transfer.getDebitAmount());

        //convert amount to target currency if necessary
        computeCreditAmount(transfer);

        log.info("Credit amount for transfer {}: {}", transfer.getId(), transfer.getCreditAmount());

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
     *  Ensures a Senders account has sufficient balance.
     *  */
    protected void computeDebit(Transfer transfer) {
        BigDecimal totalDebit = transfer.getAmount().add(transfer.getFeeApplied()); // amount + fee
        transfer.setDebitAmount(totalDebit);

    }

    protected void inSufficientBalanceCheck(Account senderAccount, BigDecimal debitAmount) throws TransferException{
        if (senderAccount.getBalance().compareTo(debitAmount) < 0) {
            throw new TransferException(
                    TransferException.Reason.INSUFFICIENT_FUNDS,
                    "Insufficient funds for this transaction");
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

        Account sender;
        Account receiver;

        // Always lock accounts in ascending order to avoid deadlocks
        if (senderId < receiverId) {
            sender = accountRepository.findById(senderId)
                    .orElseThrow(() -> new TransferException(
                            TransferException.Reason.ACCOUNT_NOT_FOUND, "Sender not found"));
            receiver = accountRepository.findById(receiverId)
                    .orElseThrow(() -> new TransferException(
                            TransferException.Reason.ACCOUNT_NOT_FOUND, "Receiver not found"));
        } else {
            receiver = accountRepository.findById(receiverId)
                    .orElseThrow(() -> new TransferException(
                            TransferException.Reason.ACCOUNT_NOT_FOUND, "Receiver not found"));
            sender = accountRepository.findById(senderId)
                    .orElseThrow(() -> new TransferException(
                            TransferException.Reason.ACCOUNT_NOT_FOUND, "Sender not found"));
        }

        log.info("Locked Accounts {} and {}", transfer.getFromAccountIdRaw(), transfer.getToAccountIdRaw());
        transfer.setFromAccount(sender);
        transfer.setToAccount(receiver);

        validateAccountsHaveCurrency(sender, receiver);
        transfer.setCurrencyFrom(sender.getCurrency());
        transfer.setCurrencyTo(receiver.getCurrency());

    }


    /**
     *  Converts the transfer amount to the target currency.
     *  This is where the FX rate is fetched from the external service.
     * */
    protected BigDecimal convertTransferAmount(Transfer transfer) throws TransferException {
        Currency from = transfer.getCurrencyFrom();
        Currency to = transfer.getCurrencyTo();
        BigDecimal originalAmount = transfer.getAmount();

        BigDecimal fxRate = fxConversionService.getRate(from, to);
        BigDecimal convertedAmount = commonHelper.multiply(fxRate, originalAmount);

        transfer.setFxRate(fxRate);

        log.info("Converted {} {} → {} {} @ rate {}",
                originalAmount, from.getCode(),
                convertedAmount, to.getCode(),
                fxRate);

        return convertedAmount;
    }
   /**
    *  computes Credit needed and sets it in the transfer record.
    *
    * */
    protected void computeCreditAmount(Transfer transfer) throws TransferException {

        if(transfer.getCurrencyTo().equals(transfer.getCurrencyFrom())) {
            transfer.setCreditAmount(transfer.getAmount());
            return;
        }

        BigDecimal convertedAmount = convertTransferAmount(transfer);
        transfer.setCreditAmount(convertedAmount);
    }


    protected void validateAccountsHaveCurrency(Account from, Account to) {
        if (from.getCurrency() == null || to.getCurrency() == null) {
            throw new TransferRuntimeException(
                    TransferRuntimeException.Reason.INVALID_ACCOUNT_STATE,
                    "One or both accounts have no assigned currency");
        }
    }
}

