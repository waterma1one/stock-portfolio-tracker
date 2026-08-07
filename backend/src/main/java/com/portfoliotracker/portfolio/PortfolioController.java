package com.portfoliotracker.portfolio;

import com.portfoliotracker.portfolio.dto.CreatePortfolioRequest;
import com.portfoliotracker.portfolio.dto.PortfolioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
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
}
