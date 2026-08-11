import { Pie } from 'react-chartjs-2';
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js';
import type { SectorAllocation } from '../types/portfolio';

ChartJS.register(ArcElement, Tooltip, Legend);

const COLORS = ['#2563eb', '#16a34a', '#dc2626', '#d97706', '#7c3aed', '#0891b2', '#db2777', '#65a30d'];

export function AllocationChart({ sectors }: { sectors: SectorAllocation[] }) {
  if (sectors.length === 0) {
    return null;
  }

  const data = {
    labels: sectors.map((s) => `${s.sector} (${s.percent.toFixed(1)}%)`),
    datasets: [
      {
        data: sectors.map((s) => s.marketValue),
        backgroundColor: sectors.map((_, i) => COLORS[i % COLORS.length]),
      },
    ],
  };

  return (
    <div className="mb-6 max-w-sm">
      <h2 className="text-lg font-semibold mb-2">Sector Allocation</h2>
      <Pie data={data} />
    </div>
  );
}
