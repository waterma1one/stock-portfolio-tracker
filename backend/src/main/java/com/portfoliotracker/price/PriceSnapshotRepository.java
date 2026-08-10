package com.portfoliotracker.price;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {
    Optional<PriceSnapshot> findTopBySymbolOrderByFetchedAtDesc(String symbol);
}
