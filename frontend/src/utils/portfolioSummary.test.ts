import { describe, it, expect } from 'vitest';
import { computePortfolioSummary } from './portfolioSummary';
import type { Holding } from '../types/portfolio';

describe('computePortfolioSummary', () => {
  it('sums market value and unrealized P&L across holdings with prices', () => {
    const holdings: Holding[] = [
      { symbol: 'AAPL', quantity: 10, avgCostBasis: 100, currentPrice: 150, marketValue: 1500, unrealizedPnl: 500 },
      { symbol: 'MSFT', quantity: 5, avgCostBasis: 300, currentPrice: 280, marketValue: 1400, unrealizedPnl: -100 },
    ];

    const summary = computePortfolioSummary(holdings);

    expect(summary.totalMarketValue).toBe(2900);
    expect(summary.totalUnrealizedPnl).toBe(400);
  });

  it('excludes holdings with no price snapshot from the totals', () => {
    const holdings: Holding[] = [
      { symbol: 'AAPL', quantity: 10, avgCostBasis: 100, currentPrice: 150, marketValue: 1500, unrealizedPnl: 500 },
      { symbol: 'ZZZZ', quantity: 5, avgCostBasis: 10, currentPrice: null, marketValue: null, unrealizedPnl: null },
    ];

    const summary = computePortfolioSummary(holdings);

    expect(summary.totalMarketValue).toBe(1500);
    expect(summary.totalUnrealizedPnl).toBe(500);
  });

  it('returns null totals when no holdings have a price snapshot', () => {
    const holdings: Holding[] = [
      { symbol: 'ZZZZ', quantity: 5, avgCostBasis: 10, currentPrice: null, marketValue: null, unrealizedPnl: null },
    ];

    const summary = computePortfolioSummary(holdings);

    expect(summary.totalMarketValue).toBeNull();
    expect(summary.totalUnrealizedPnl).toBeNull();
  });

  it('returns null totals for an empty holdings list', () => {
    const summary = computePortfolioSummary([]);

    expect(summary.totalMarketValue).toBeNull();
    expect(summary.totalUnrealizedPnl).toBeNull();
  });
});
