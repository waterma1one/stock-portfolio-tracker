import { useEffect, useState } from 'react';
import { PortfolioList } from '../components/PortfolioList';
import { HoldingsTable } from '../components/HoldingsTable';
import { PortfolioSummary } from '../components/PortfolioSummary';
import { TransactionForm } from '../components/TransactionForm';
import { TransactionList } from '../components/TransactionList';
import { AllocationChart } from '../components/AllocationChart';
import { PerformanceChart } from '../components/PerformanceChart';
import { PnlBreakdown } from '../components/PnlBreakdown';
import { getPnl } from '../api/portfolios';
import {
  getPortfolios, createPortfolio, getHoldings, getTransactions,
  createTransaction, deleteTransaction, getAllocation, getPerformance,
} from '../api/portfolios';
import type { Portfolio, Holding, Transaction, CreateTransactionInput, SectorAllocation, PerformancePoint, PnlResponse } from '../types/portfolio';

export function Dashboard() {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [sectors, setSectors] = useState<SectorAllocation[]>([]);
  const [performance, setPerformance] = useState<PerformancePoint[]>([]);
  const [pnl, setPnl] = useState<PnlResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getPortfolios()
      .then((ps) => {
        setPortfolios(ps);
        if (ps.length > 0) setSelectedId(ps[0].id);
      })
      .catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    if (selectedId === null) return;
    getHoldings(selectedId).then(setHoldings).catch((e) => setError(e.message));
    getTransactions(selectedId).then(setTransactions).catch((e) => setError(e.message));
    getAllocation(selectedId).then((r) => setSectors(r.sectors)).catch((e) => setError(e.message));
    getPerformance(selectedId).then((r) => setPerformance(r.points)).catch((e) => setError(e.message));
    getPnl(selectedId).then(setPnl).catch((e) => setError(e.message));
  }, [selectedId]);

  const handleCreatePortfolio = async (name: string) => {
    try {
      const p = await createPortfolio(name);
      setPortfolios((prev) => [...prev, p]);
      setSelectedId(p.id);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const refresh = async (portfolioId: number) => {
    setHoldings(await getHoldings(portfolioId));
    setTransactions(await getTransactions(portfolioId));
    setSectors((await getAllocation(portfolioId)).sectors);
    setPerformance((await getPerformance(portfolioId)).points);
    setPnl(await getPnl(portfolioId));
  };

  const handleAddTransaction = async (input: CreateTransactionInput) => {
    if (selectedId === null) return;
    try {
      await createTransaction(selectedId, input);
      await refresh(selectedId);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const handleDeleteTransaction = async (id: number) => {
    try {
      await deleteTransaction(id);
      if (selectedId !== null) await refresh(selectedId);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div className="min-h-screen">
      {error && <div className="bg-red-100 text-red-700 p-2 rounded m-4">{error}</div>}
      <div className="flex">
      <PortfolioList
        portfolios={portfolios}
        selectedId={selectedId}
        onSelect={setSelectedId}
        onCreate={handleCreatePortfolio}
      />
      <main className="flex-1 p-6">
        {selectedId === null ? (
          <p className="text-gray-500">Create a portfolio to get started.</p>
        ) : (
          <>
            <h1 className="text-xl font-semibold mb-4">Holdings</h1>
            <PortfolioSummary holdings={holdings} />
            <AllocationChart sectors={sectors} />
            <PerformanceChart points={performance} />
            <PnlBreakdown pnl={pnl} />
            <HoldingsTable holdings={holdings} />
            <h1 className="text-xl font-semibold mb-2">Transactions</h1>
            <TransactionForm onSubmit={handleAddTransaction} />
            <TransactionList transactions={transactions} onDelete={handleDeleteTransaction} />
          </>
        )}
      </main>
      </div>
    </div>
  );
}
