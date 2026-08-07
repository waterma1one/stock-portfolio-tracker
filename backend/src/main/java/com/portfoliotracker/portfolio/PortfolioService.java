package com.portfoliotracker.portfolio;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public Portfolio create(String name) {
        Portfolio portfolio = new Portfolio();
        portfolio.setName(name);
        portfolio.setCreatedAt(Instant.now());
        return portfolioRepository.save(portfolio);
    }

    public List<Portfolio> findAll() {
        return portfolioRepository.findAll();
    }
}
