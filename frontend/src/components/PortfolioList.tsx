import { useState } from 'react';
import type { Portfolio } from '../types/portfolio';

interface Props {
  portfolios: Portfolio[];
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreate: (name: string) => void;
}

export function PortfolioList({ portfolios, selectedId, onSelect, onCreate }: Props) {
  const [name, setName] = useState('');

  return (
    <div className="w-64 border-r border-gray-200 p-4">
      <h2 className="text-lg font-semibold mb-2">Portfolios</h2>
      <ul className="mb-4">
        {portfolios.map((p) => (
          <li key={p.id}>
            <button
              className={`w-full text-left px-2 py-1 rounded ${p.id === selectedId ? 'bg-blue-100' : 'hover:bg-gray-100'}`}
              onClick={() => onSelect(p.id)}
            >
              {p.name}
            </button>
          </li>
        ))}
      </ul>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (!name.trim()) return;
          onCreate(name.trim());
          setName('');
        }}
        className="flex gap-2"
      >
        <input
          className="border rounded px-2 py-1 flex-1 text-sm"
          placeholder="New portfolio"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button type="submit" className="bg-blue-600 text-white px-2 py-1 rounded text-sm">
          Add
        </button>
      </form>
    </div>
  );
}
