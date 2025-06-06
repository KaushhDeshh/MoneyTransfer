package com.jpmc.moneytransfer.moneytransfer.account.repository;

import com.jpmc.moneytransfer.moneytransfer.account.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface AccountRepository  extends JpaRepository<Account, Long>{

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findById(Long id);
}
