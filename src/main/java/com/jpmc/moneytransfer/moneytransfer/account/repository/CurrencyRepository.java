package com.jpmc.moneytransfer.moneytransfer.account.repository;

import com.jpmc.moneytransfer.moneytransfer.account.model.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, String> {

    Optional<Currency> findByCode(String code);
}
