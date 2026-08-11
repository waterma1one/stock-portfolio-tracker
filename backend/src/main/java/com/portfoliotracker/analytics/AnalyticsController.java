package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.AllocationResponse;
import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.holding.HoldingCalculator;
import com.portfoliotracker.portfolio.PortfolioRepository;
import com.portfoliotracker.price.PriceService;
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

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/analytics")
public class AnalyticsController {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final PriceService priceService;
    private final SymbolProfileService symbolProfileService;

    public AnalyticsController(PortfolioRepository portfolioRepository,
                                TransactionRepository transactionRepository,
                                PriceService priceService,
                                SymbolProfileService symbolProfileService) {
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.priceService = priceService;
        this.symbolProfileService = symbolProfileService;
    }

    @GetMapping("/allocation")
    public AllocationResponse allocation(@PathVariable Long portfolioId) {
        List<Holding> holdings = holdingsFor(portfolioId);
        return AllocationCalculator.calculate(holdings,
                symbol -> priceService.getLatest(symbol).map(com.portfoliotracker.price.PriceSnapshot::getPrice),
                symbol -> symbolProfileService.getProfile(symbol).map(com.portfoliotracker.symbolprofile.SymbolProfile::getSector));
    }

    private List<Holding> holdingsFor(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + portfolioId);
        }
        List<Transaction> txs = transactionRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        return HoldingCalculator.calculate(txs);
    }
}
