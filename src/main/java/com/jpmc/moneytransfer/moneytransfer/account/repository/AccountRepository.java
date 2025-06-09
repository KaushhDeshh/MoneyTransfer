package com.jpmc.moneytransfer.moneytransfer.account.repository;

import com.jpmc.moneytransfer.moneytransfer.account.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository  extends JpaRepository<Account, Long>{

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findById(Long id);


    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findUnlockedById(@Param("id") Long id);
}
