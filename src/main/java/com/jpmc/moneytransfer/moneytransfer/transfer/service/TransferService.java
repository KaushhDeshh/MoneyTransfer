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
     *  Transfer money from one account to another
     * @param transferRequestDTO
     * @return TransferRecord id
     * @throws TransferRuntimeException
     * @throws TransferException
     */
    public Transfer transferMoney(TransferRequestDTO transferRequestDTO) throws TransferRuntimeException, TransferException {
        // create a transfer record and save it
        Transfer transfer = createTransferRecord(transferRequestDTO);

        // process transfer record
        processTransfer(transfer);

        return transfer;
    }

    /**
     * Used to create Transfer Record sets unverified Account ID's for logging and auditing purposes
     * @param transferRequestDTO
     */
    protected Transfer createTransferRecord(TransferRequestDTO transferRequestDTO) throws TransferException, TransferRuntimeException {

        Transfer transfer = new Transfer(
                transferRequestDTO.getSenderAccountId(),
                transferRequestDTO.getReceiverAccountId(),
                transferRequestDTO.getAmount(),
                TransferState.PROCESSING);

        return transfer;
    }

    /**
     *  Opens a DB Transaction and saves a Transfer Record
     * @param transfer
     * @param transferRequestDTO
     * @return TransferRecord id
     * */

    @Transactional
    protected Long createAndSaveTransfer(Transfer transfer, TransferRequestDTO transferRequestDTO) throws TransferException ,TransferRuntimeException{
        try {
            Currency currency = getCurrencyFromDTO(transferRequestDTO.getCurrency());
            transfer.setCurrency(currency);
            Transfer saved = transferRepository.save(transfer);
            log.info("Transfer transfer {} created (from={} to={} amt={} {})",
                    saved.getId(),
                    saved.getFromAccountIdRaw(), saved.getToAccountIdRaw(),
                    saved.getAmount(), saved.getCurrencyFrom());

            return saved.getId();

        } catch (DataIntegrityViolationException dive) {

            log.error("Failed to create transfer transfer {}", transfer, dive);

            throw new TransferRuntimeException(
                    TransferRuntimeException.Reason.DB_CONSTRAINT_VIOLATION,
                    "Could not create transfer record", dive);
        }
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
     *  Process the transfer record. This is where the actual transfer is processed.
     *  We can Process Transfer Fee before because Since its cached it doesn't need a db lookup to calculate.
     * */
    protected void processTransfer(Transfer transfer) throws TransferException, TransferRuntimeException{

        try {
            // Preprocess transfer fee (cached, no DB call)
            processTransferFee(transfer);

            //check if Account IDs are no the same
            if(transfer.getFromAccountIdRaw().equals(transfer.getToAccountIdRaw())){
                throw new TransferException(
                        TransferException.Reason.SELF_TRANSFER,
                        "Self transfer not allowed");
            }

            // Preform DB Transaction
            transfer = performTransfer(transfer);


        } catch (TransferException | TransferRuntimeException ex) {
            //cancels the DB Transaction and sets the transfer record to FAILED
            updateTransferRecordAsFailed(transfer);
            throw ex;
        }

    }


    /**
     * Calculates and applies the transfer fee.
     */
    protected void processTransferFee(Transfer transfer) {

        try {

            BigDecimal fee = feeService.calculateFee(transfer.getAmount());
            transfer.setFeeApplied(fee);

            log.info("Fee calculated for transfer {}: {}", transfer.getId(), fee);

        } catch (Exception e) { // for external service failure

            log.error("Fee calculation failed for transfer {}: {}", transfer.getId(), e.getMessage(), e);

            throw new TransferRuntimeException(
                    TransferRuntimeException.Reason.UNKNOWN_ERROR,
                    "Fee service failure", e);

        }
    }


    /**
     * Marks the transfer record as FAILED and persists the update.
     */
    @Transactional
    protected void updateTransferRecordAsFailed(Transfer transfer) throws TransferRuntimeException {
        try {

            transfer.setState(TransferState.FAILED);
            transferRepository.save(transfer);

            log.warn("Transfer {} marked as FAILED", transfer.getId());

        } catch (Exception e) {

            log.error("Failed to update transfer {} as FAILED", transfer.getId(), e);

            throw new TransferRuntimeException(
                    TransferRuntimeException.Reason.DB_ERROR,
                    "Failed to mark transfer as failed", e);
        }
    }


    /**
     *  Preforms the DB Transaction and marks Transfer as Completed.
     * */

    @Transactional(isolation = Isolation.READ_COMMITTED)
    protected Transfer performTransfer(Transfer transfer) throws TransferException {
        // Locking Accounts
        attachLockedAccountsOrdered(transfer);
        Account sender = transfer.getFromAccount();
        Account receiver = transfer.getToAccount();

        //setting currency
        transfer.setCurrencyFrom(sender.getCurrency());
        transfer.setCurrencyTo(receiver.getCurrency());

        //Check if Currency is equal to Sender's Account Currency
        if(!transfer.getCurrencyFrom().equals(transfer.getCurrency())){
            throw new TransferException(
                    TransferException.Reason.INVALID_CURRENCY,
                    "Currency mismatch from DTO and Sender's Account");
        }

        //Check if Sender has sufficient funds
        BigDecimal totalDebit = transfer.getAmount().add(transfer.getFeeApplied()); // amount + fee
        transfer.setDebitAmount(totalDebit);
        BigDecimal senderBalance = sender.getBalance();


        if (senderBalance.compareTo(totalDebit) < 0) {
            throw new TransferException(
                    TransferException.Reason.INSUFFICIENT_FUNDS,
                    "Sender balance: " + senderBalance + ", required: " + totalDebit);
        }

        //Check if Conversion is required
        if(!transfer.getCurrencyTo().equals(transfer.getCurrencyFrom())){

            BigDecimal creditAmount = convertTransferAmount(transfer);
            transfer.setCreditAmount(creditAmount);

        } else{

            transfer.setCreditAmount(transfer.getAmount());

        }

        //these accounts are currently locked so we can debit and credit them safely
        sender.debit(transfer.getDebitAmount());
        receiver.credit(transfer.getCreditAmount());

        //mark a transfer as completed
        transfer.setState(TransferState.COMPLETED);

        return transfer;
    }

    /**
     *  Attaches the accounts to the transfer record.
     *  The accounts are attached in ascending order to avoid deadlocks.
     *  This is where the accounts are locked.
     * */
    protected void attachLockedAccountsOrdered(Transfer transfer) throws TransferException {
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

        transfer.setFromAccount(sender);
        transfer.setToAccount(receiver);
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
}

