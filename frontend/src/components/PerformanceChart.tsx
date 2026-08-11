import { Line } from 'react-chartjs-2';
import {
  Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend,
} from 'chart.js';
import type { PerformancePoint } from '../types/portfolio';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend);

export function PerformanceChart({ points }: { points: PerformancePoint[] }) {
  if (points.length === 0) {
    return null;
  }

  const data = {
    labels: points.map((p) => p.date),
    datasets: [
      {
        label: 'Portfolio',
        data: points.map((p) => p.portfolioChangePercent),
        borderColor: '#2563eb',
        backgroundColor: '#2563eb',
      },
      {
        label: 'SPY',
        data: points.map((p) => p.spyChangePercent),
        borderColor: '#6b7280',
        backgroundColor: '#6b7280',
      },
    ],
  };

  return (
    <div className="mb-6 max-w-2xl">
      <h2 className="text-lg font-semibold mb-2">Performance vs SPY</h2>
      <Line data={data} options={{ scales: { y: { ticks: { callback: (v) => `${v}%` } } } }} />
    </div>
  );
}
