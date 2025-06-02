package com.jpmc.moneytransfer.moneytransfer.transfer.repository;

import com.jpmc.moneytransfer.moneytransfer.transfer.model.TransferPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferPolicyRepository extends JpaRepository<TransferPolicy, String> {
}
