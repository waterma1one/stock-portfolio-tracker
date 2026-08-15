import type { Holding } from '../types/portfolio';

export interface PortfolioSummaryTotals {
  totalMarketValue: number | null;
  totalUnrealizedPnl: number | null;
}

export function computePortfolioSummary(holdings: Holding[]): PortfolioSummaryTotals {
  const withPrices = holdings.filter((h) => h.marketValue !== null && h.unrealizedPnl !== null);

  if (withPrices.length === 0) {
    return { totalMarketValue: null, totalUnrealizedPnl: null };
  }

  const totalMarketValue = withPrices.reduce((sum, h) => sum + (h.marketValue ?? 0), 0);
  const totalUnrealizedPnl = withPrices.reduce((sum, h) => sum + (h.unrealizedPnl ?? 0), 0);

  return { totalMarketValue, totalUnrealizedPnl };
}
