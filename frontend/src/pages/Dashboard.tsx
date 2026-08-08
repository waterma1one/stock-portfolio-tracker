import { useEffect, useState } from 'react';
import { PortfolioList } from '../components/PortfolioList';
import { HoldingsTable } from '../components/HoldingsTable';
import { TransactionForm } from '../components/TransactionForm';
import { TransactionList } from '../components/TransactionList';
import {
  getPortfolios, createPortfolio, getHoldings, getTransactions,
  createTransaction, deleteTransaction,
} from '../api/portfolios';
import type { Portfolio, Holding, Transaction, CreateTransactionInput } from '../types/portfolio';

export function Dashboard() {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);

  useEffect(() => {
    getPortfolios().then((ps) => {
      setPortfolios(ps);
      if (ps.length > 0) setSelectedId(ps[0].id);
    });
  }, []);

  useEffect(() => {
    if (selectedId === null) return;
    getHoldings(selectedId).then(setHoldings);
    getTransactions(selectedId).then(setTransactions);
  }, [selectedId]);

  const handleCreatePortfolio = async (name: string) => {
    const p = await createPortfolio(name);
    setPortfolios((prev) => [...prev, p]);
    setSelectedId(p.id);
  };

  const refresh = async (portfolioId: number) => {
    setHoldings(await getHoldings(portfolioId));
    setTransactions(await getTransactions(portfolioId));
  };

  const handleAddTransaction = async (input: CreateTransactionInput) => {
    if (selectedId === null) return;
    await createTransaction(selectedId, input);
    await refresh(selectedId);
  };

  const handleDeleteTransaction = async (id: number) => {
    await deleteTransaction(id);
    if (selectedId !== null) await refresh(selectedId);
  };

  return (
    <div className="flex min-h-screen">
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
            <HoldingsTable holdings={holdings} />
            <h1 className="text-xl font-semibold mb-2">Transactions</h1>
            <TransactionForm onSubmit={handleAddTransaction} />
            <TransactionList transactions={transactions} onDelete={handleDeleteTransaction} />
          </>
        )}
      </main>
    </div>
  );
}
