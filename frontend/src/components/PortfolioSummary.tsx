import type { Holding } from '../types/portfolio';
import { computePortfolioSummary } from '../utils/portfolioSummary';

export function PortfolioSummary({ holdings }: { holdings: Holding[] }) {
  const { totalMarketValue, totalUnrealizedPnl } = computePortfolioSummary(holdings);

  if (totalMarketValue === null || totalUnrealizedPnl === null) {
    return null;
  }

  const pnlClass = totalUnrealizedPnl < 0 ? 'text-red-600' : 'text-green-600';

  return (
    <div className="flex gap-6 mb-4">
      <div>
        <div className="text-xs text-gray-500">Total Market Value</div>
        <div className="text-lg font-semibold">${totalMarketValue.toFixed(2)}</div>
      </div>
      <div>
        <div className="text-xs text-gray-500">Unrealized P&L</div>
        <div className={`text-lg font-semibold ${pnlClass}`}>
          {totalUnrealizedPnl >= 0 ? '+' : ''}${totalUnrealizedPnl.toFixed(2)}
        </div>
      </div>
    </div>
  );
}
