package com.portfoliotracker.portfolio.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePortfolioRequest(@NotBlank String name) {
}
