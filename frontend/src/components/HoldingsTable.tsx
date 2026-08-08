import type { Holding } from '../types/portfolio';

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
        </tr>
      </thead>
      <tbody>
        {holdings.map((h) => (
          <tr key={h.symbol} className="border-b">
            <td className="py-1">{h.symbol}</td>
            <td className="py-1">{h.quantity}</td>
            <td className="py-1">${h.avgCostBasis.toFixed(2)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
