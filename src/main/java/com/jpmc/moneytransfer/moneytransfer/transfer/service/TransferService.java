package com.jpmc.moneytransfer.moneytransfer.transfer.service;

import com.jpmc.moneytransfer.moneytransfer.transfer.model.TransferRequestDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 *  Service for Transferring Money from one account to another
 * */
@Service
public class TransferService {


    // transfer money we want to return TransferRecord id
    public void transferMoney(TransferRequestDTO transferRequestDTO) {
        //create Transfer Record
        //set state
        // grab fees
        // calculate fees
        //

    }



    //transferMoney but allows retry if Transaction time out or Row lock timeout
    public void transferMoneyWithRetry(TransferRequestDTO transferRequestDTO) {


    }



}
