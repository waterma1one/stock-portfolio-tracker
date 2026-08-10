import type { Holding } from '../types/portfolio';

function formatCurrency(value: number | null): string {
  return value === null ? '—' : `$${value.toFixed(2)}`;
}

export function HoldingsTable({ holdings }: { holdings: Holding[] }) {
  if (holdings.length === 0) {
    return <p className="text-gray-500 text-sm">No holdings yet — add a transaction below.</p>;
  }

  return (
    <table className="w-full text-sm mb-6">
      <thead>
        <tr className="text-left border-b">
          <th className="py-1">Symbol</th>
          <th className="py-1">Quantity</th>
          <th className="py-1">Avg Cost Basis</th>
          <th className="py-1">Current Price</th>
          <th className="py-1">Market Value</th>
          <th className="py-1">Unrealized P&L</th>
        </tr>
      </thead>
      <tbody>
        {holdings.map((h) => (
          <tr key={h.symbol} className="border-b">
            <td className="py-1">{h.symbol}</td>
            <td className="py-1">{h.quantity}</td>
            <td className="py-1">${h.avgCostBasis.toFixed(2)}</td>
            <td className="py-1">{formatCurrency(h.currentPrice)}</td>
            <td className="py-1">{formatCurrency(h.marketValue)}</td>
            <td className={`py-1 ${h.unrealizedPnl !== null && h.unrealizedPnl < 0 ? 'text-red-600' : 'text-green-600'}`}>
              {h.unrealizedPnl === null ? '—' : `${h.unrealizedPnl >= 0 ? '+' : ''}$${h.unrealizedPnl.toFixed(2)}`}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
