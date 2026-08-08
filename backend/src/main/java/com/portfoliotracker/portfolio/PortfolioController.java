package com.portfoliotracker.portfolio;

import com.portfoliotracker.portfolio.dto.CreatePortfolioRequest;
import com.portfoliotracker.portfolio.dto.PortfolioResponse;
import com.portfoliotracker.transaction.TransactionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final TransactionRepository transactionRepository;

    public PortfolioController(PortfolioService portfolioService,
                                TransactionRepository transactionRepository) {
        this.portfolioService = portfolioService;
        this.transactionRepository = transactionRepository;
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
        List<com.portfoliotracker.transaction.Transaction> txs =
                transactionRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        return com.portfoliotracker.holding.HoldingCalculator.calculate(txs).stream()
                .map(com.portfoliotracker.holding.dto.HoldingResponse::from)
                .toList();
    }
}
