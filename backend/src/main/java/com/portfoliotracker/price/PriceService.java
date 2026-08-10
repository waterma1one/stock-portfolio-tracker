package com.portfoliotracker.price;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class PriceService {

    private final PriceProvider priceProvider;
    private final PriceSnapshotRepository priceSnapshotRepository;

    public PriceService(PriceProvider priceProvider, PriceSnapshotRepository priceSnapshotRepository) {
        this.priceProvider = priceProvider;
        this.priceSnapshotRepository = priceSnapshotRepository;
    }

    public void fetchAndStore(String symbol) {
        priceProvider.getPrice(symbol).ifPresent(price -> {
            PriceSnapshot snapshot = new PriceSnapshot();
            snapshot.setSymbol(symbol);
            snapshot.setPrice(price);
            snapshot.setFetchedAt(Instant.now());
            priceSnapshotRepository.save(snapshot);
        });
    }

    public Optional<PriceSnapshot> getLatest(String symbol) {
        return priceSnapshotRepository.findTopBySymbolOrderByFetchedAtDesc(symbol.trim().toUpperCase());
    }
}
