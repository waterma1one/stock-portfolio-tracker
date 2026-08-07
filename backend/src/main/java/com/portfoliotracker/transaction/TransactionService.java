package com.portfoliotracker.transaction;

import com.portfoliotracker.transaction.dto.CreateTransactionRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction create(Long portfolioId, CreateTransactionRequest request) {
        Transaction transaction = Transaction.of(request.symbol(), request.type(),
                request.quantity(), request.price(), request.executedAt());
        transaction.setPortfolioId(portfolioId);
        return transactionRepository.save(transaction);
    }

    public List<Transaction> findByPortfolio(Long portfolioId) {
        return transactionRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
    }

    public void delete(Long transactionId) {
        transactionRepository.deleteById(transactionId);
    }
}
