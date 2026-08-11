package com.portfoliotracker.analytics.dto;

import java.math.BigDecimal;

public record SectorAllocation(String sector, BigDecimal marketValue, BigDecimal percent) {
}
