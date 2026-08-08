import { useState } from 'react';
import type { CreateTransactionInput, TransactionType } from '../types/portfolio';

export function TransactionForm({ onSubmit }: { onSubmit: (input: CreateTransactionInput) => void }) {
  const [symbol, setSymbol] = useState('');
  const [type, setType] = useState<TransactionType>('BUY');
  const [quantity, setQuantity] = useState('');
  const [price, setPrice] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!symbol.trim() || !quantity || !price) return;
    onSubmit({
      symbol: symbol.trim().toUpperCase(),
      type,
      quantity: Number(quantity),
      price: Number(price),
      executedAt: new Date().toISOString(),
    });
    setSymbol('');
    setQuantity('');
    setPrice('');
  };

  return (
    <form onSubmit={handleSubmit} className="flex gap-2 items-end mb-4 flex-wrap">
      <div>
        <label className="block text-xs text-gray-500">Symbol</label>
        <input className="border rounded px-2 py-1 w-24" value={symbol} onChange={(e) => setSymbol(e.target.value)} />
      </div>
      <div>
        <label className="block text-xs text-gray-500">Type</label>
        <select className="border rounded px-2 py-1" value={type} onChange={(e) => setType(e.target.value as TransactionType)}>
          <option value="BUY">BUY</option>
          <option value="SELL">SELL</option>
        </select>
      </div>
      <div>
        <label className="block text-xs text-gray-500">Quantity</label>
        <input className="border rounded px-2 py-1 w-24" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
      </div>
      <div>
        <label className="block text-xs text-gray-500">Price</label>
        <input className="border rounded px-2 py-1 w-24" value={price} onChange={(e) => setPrice(e.target.value)} />
      </div>
      <button type="submit" className="bg-blue-600 text-white px-3 py-1 rounded">
        Add Transaction
      </button>
    </form>
  );
}
