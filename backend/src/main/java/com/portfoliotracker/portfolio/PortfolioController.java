package com.portfoliotracker.portfolio;

import com.portfoliotracker.portfolio.dto.CreatePortfolioRequest;
import com.portfoliotracker.portfolio.dto.PortfolioResponse;
import com.portfoliotracker.price.PriceService;
import com.portfoliotracker.transaction.TransactionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final PriceService priceService;

    public PortfolioController(PortfolioService portfolioService,
                                PortfolioRepository portfolioRepository,
                                TransactionRepository transactionRepository,
                                PriceService priceService) {
        this.portfolioService = portfolioService;
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.priceService = priceService;
    }

    @GetMapping
    public List<PortfolioResponse> list() {
        return portfolioService.findAll().stream().map(PortfolioResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioResponse create(@Valid @RequestBody CreatePortfolioRequest request) {
        return PortfolioResponse.from(portfolioService.create(request.name()));
    }

    @GetMapping("/{portfolioId}/holdings")
    public List<com.portfoliotracker.holding.dto.HoldingResponse> holdings(@PathVariable Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + portfolioId);
        }
        List<com.portfoliotracker.transaction.Transaction> txs =
                transactionRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        return com.portfoliotracker.holding.HoldingCalculator.calculate(txs).stream()
                .map(h -> com.portfoliotracker.holding.dto.HoldingResponse.from(h, priceService.getLatest(h.symbol())))
                .toList();
    }
}
