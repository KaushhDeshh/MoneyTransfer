package com.jpmc.moneytransfer.moneytransfer.transfer.repository;

import com.jpmc.moneytransfer.moneytransfer.transfer.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {
}
