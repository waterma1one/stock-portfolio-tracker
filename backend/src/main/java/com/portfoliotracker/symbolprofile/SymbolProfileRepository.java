package com.portfoliotracker.symbolprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SymbolProfileRepository extends JpaRepository<SymbolProfile, String> {
    Optional<SymbolProfile> findBySymbol(String symbol);
}
