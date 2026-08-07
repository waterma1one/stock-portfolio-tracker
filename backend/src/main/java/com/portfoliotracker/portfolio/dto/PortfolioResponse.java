package com.portfoliotracker.portfolio.dto;

import com.portfoliotracker.portfolio.Portfolio;

import java.time.Instant;

public record PortfolioResponse(Long id, String name, Instant createdAt) {
    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(portfolio.getId(), portfolio.getName(), portfolio.getCreatedAt());
    }
}
