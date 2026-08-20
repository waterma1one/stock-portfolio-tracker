package com.portfoliotracker.pricehistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findBySymbolOrderByPriceDateAsc(String symbol);

    @Modifying
    @Query("delete from PriceHistory p where p.symbol = :symbol")
    void deleteBySymbol(@Param("symbol") String symbol);
}
