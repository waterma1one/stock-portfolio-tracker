package com.portfoliotracker.transaction;

import com.portfoliotracker.portfolio.PortfolioRepository;
import com.portfoliotracker.transaction.dto.CreateTransactionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;

    public TransactionService(TransactionRepository transactionRepository, PortfolioRepository portfolioRepository) {
        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
    }

    public Transaction create(Long portfolioId, CreateTransactionRequest request) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + portfolioId);
        }
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
