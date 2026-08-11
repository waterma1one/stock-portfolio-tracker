import type { PnlResponse } from '../types/portfolio';

function formatPnl(value: number): string {
  return `${value >= 0 ? '+' : ''}$${value.toFixed(2)}`;
}

export function PnlBreakdown({ pnl }: { pnl: PnlResponse | null }) {
  if (pnl === null) {
    return null;
  }

  const totalPnl = pnl.realizedPnl + pnl.unrealizedPnl;

  return (
    <div className="flex gap-6 mb-6">
      <div>
        <div className="text-xs text-gray-500">Realized P&L</div>
        <div className={`text-lg font-semibold ${pnl.realizedPnl < 0 ? 'text-red-600' : 'text-green-600'}`}>
          {formatPnl(pnl.realizedPnl)}
        </div>
      </div>
      <div>
        <div className="text-xs text-gray-500">Unrealized P&L</div>
        <div className={`text-lg font-semibold ${pnl.unrealizedPnl < 0 ? 'text-red-600' : 'text-green-600'}`}>
          {formatPnl(pnl.unrealizedPnl)}
        </div>
      </div>
      <div>
        <div className="text-xs text-gray-500">Total P&L</div>
        <div className={`text-lg font-semibold ${totalPnl < 0 ? 'text-red-600' : 'text-green-600'}`}>
          {formatPnl(totalPnl)}
        </div>
      </div>
    </div>
  );
}
