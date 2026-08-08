import type { Transaction } from '../types/portfolio';

interface Props {
  transactions: Transaction[];
  onDelete: (id: number) => void;
}

export function TransactionList({ transactions, onDelete }: Props) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left border-b">
          <th className="py-1">Date</th>
          <th className="py-1">Symbol</th>
          <th className="py-1">Type</th>
          <th className="py-1">Qty</th>
          <th className="py-1">Price</th>
          <th className="py-1"></th>
        </tr>
      </thead>
      <tbody>
        {transactions.map((t) => (
          <tr key={t.id} className="border-b">
            <td className="py-1">{new Date(t.executedAt).toLocaleDateString()}</td>
            <td className="py-1">{t.symbol}</td>
            <td className="py-1">{t.type}</td>
            <td className="py-1">{t.quantity}</td>
            <td className="py-1">${t.price.toFixed(2)}</td>
            <td className="py-1">
              <button className="text-red-600 text-xs" onClick={() => onDelete(t.id)}>
                Delete
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
