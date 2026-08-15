package com.portfoliotracker.analytics.dto;

import java.math.BigDecimal;

public record PnlResponse(BigDecimal realizedPnl, BigDecimal unrealizedPnl) {
}
