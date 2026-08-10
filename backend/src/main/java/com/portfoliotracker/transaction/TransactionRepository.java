package com.portfoliotracker.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByPortfolioIdOrderByExecutedAtAsc(Long portfolioId);

    @Query("SELECT DISTINCT t.symbol FROM Transaction t")
    List<String> findDistinctSymbols();
}
