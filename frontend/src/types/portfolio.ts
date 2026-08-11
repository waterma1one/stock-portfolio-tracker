export interface Portfolio {
  id: number;
  name: string;
  createdAt: string;
}

export interface Holding {
  symbol: string;
  quantity: number;
  avgCostBasis: number;
  currentPrice: number | null;
  marketValue: number | null;
  unrealizedPnl: number | null;
}

export type TransactionType = 'BUY' | 'SELL';

export interface Transaction {
  id: number;
  portfolioId: number;
  symbol: string;
  type: TransactionType;
  quantity: number;
  price: number;
  executedAt: string;
}

export interface CreateTransactionInput {
  symbol: string;
  type: TransactionType;
  quantity: number;
  price: number;
  executedAt: string;
}

export interface SectorAllocation {
  sector: string;
  marketValue: number;
  percent: number;
}

export interface AllocationResponse {
  sectors: SectorAllocation[];
}
