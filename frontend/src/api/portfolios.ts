import { apiFetch } from './client';
import type { Portfolio, Holding, Transaction, CreateTransactionInput, AllocationResponse } from '../types/portfolio';

export const getPortfolios = () => apiFetch<Portfolio[]>('/api/portfolios');

export const createPortfolio = (name: string) =>
  apiFetch<Portfolio>('/api/portfolios', { method: 'POST', body: JSON.stringify({ name }) });

export const getHoldings = (portfolioId: number) =>
  apiFetch<Holding[]>(`/api/portfolios/${portfolioId}/holdings`);

export const getTransactions = (portfolioId: number) =>
  apiFetch<Transaction[]>(`/api/portfolios/${portfolioId}/transactions`);

export const createTransaction = (portfolioId: number, input: CreateTransactionInput) =>
  apiFetch<Transaction>(`/api/portfolios/${portfolioId}/transactions`, {
    method: 'POST',
    body: JSON.stringify(input),
  });

export const deleteTransaction = (transactionId: number) =>
  apiFetch<void>(`/api/transactions/${transactionId}`, { method: 'DELETE' });

export const getAllocation = (portfolioId: number) =>
  apiFetch<AllocationResponse>(`/api/portfolios/${portfolioId}/analytics/allocation`);
