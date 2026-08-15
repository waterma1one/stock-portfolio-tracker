package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.AllocationResponse;
import com.portfoliotracker.analytics.dto.PerformanceResponse;
import com.portfoliotracker.analytics.dto.PnlResponse;
import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.holding.HoldingCalculator;
import com.portfoliotracker.portfolio.PortfolioRepository;
import com.portfoliotracker.price.PriceService;
import com.portfoliotracker.pricehistory.PriceHistory;
import com.portfoliotracker.pricehistory.PriceHistoryService;
import com.portfoliotracker.symbolprofile.SymbolProfileService;
import com.portfoliotracker.transaction.Transaction;
import com.portfoliotracker.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/analytics")
public class AnalyticsController {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final PriceService priceService;
    private final SymbolProfileService symbolProfileService;
    private final PriceHistoryService priceHistoryService;

    public AnalyticsController(PortfolioRepository portfolioRepository,
                                TransactionRepository transactionRepository,
                                PriceService priceService,
                                SymbolProfileService symbolProfileService,
                                PriceHistoryService priceHistoryService) {
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.priceService = priceService;
        this.symbolProfileService = symbolProfileService;
        this.priceHistoryService = priceHistoryService;
    }

    @GetMapping("/allocation")
    public AllocationResponse allocation(@PathVariable Long portfolioId) {
        List<Holding> holdings = holdingsFor(portfolioId);
        return AllocationCalculator.calculate(holdings,
                symbol -> priceService.getLatest(symbol).map(com.portfoliotracker.price.PriceSnapshot::getPrice),
                symbol -> symbolProfileService.getProfile(symbol).map(com.portfoliotracker.symbolprofile.SymbolProfile::getSector));
    }

    @GetMapping("/performance")
    public PerformanceResponse performance(@PathVariable Long portfolioId) {
        requirePortfolioExists(portfolioId);
        List<Transaction> transactions = transactionRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        Map<String, List<PriceHistory>> historyBySymbol = transactions.stream()
                .map(Transaction::getSymbol)
                .distinct()
                .collect(Collectors.toMap(symbol -> symbol, priceHistoryService::getHistory));
        List<PriceHistory> spyHistory = priceHistoryService.getHistory("SPY");
        return PerformanceCalculator.calculate(transactions, historyBySymbol, spyHistory);
    }

    @GetMapping("/pnl")
    public PnlResponse pnl(@PathVariable Long portfolioId) {
        requirePortfolioExists(portfolioId);
        List<Transaction> transactions = transactionRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        return PnlCalculator.calculate(transactions,
                symbol -> priceService.getLatest(symbol).map(com.portfoliotracker.price.PriceSnapshot::getPrice));
    }

    private List<Holding> holdingsFor(Long portfolioId) {
        requirePortfolioExists(portfolioId);
        List<Transaction> txs = transactionRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        return HoldingCalculator.calculate(txs);
    }

    private void requirePortfolioExists(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + portfolioId);
        }
    }
}
