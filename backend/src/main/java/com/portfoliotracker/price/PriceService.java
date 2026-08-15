package com.portfoliotracker.price;

import com.portfoliotracker.common.SymbolNormalizer;
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
        String normalized = SymbolNormalizer.normalize(symbol);
        priceProvider.getPrice(normalized).ifPresent(price -> {
            PriceSnapshot snapshot = new PriceSnapshot();
            snapshot.setSymbol(normalized);
            snapshot.setPrice(price);
            snapshot.setFetchedAt(Instant.now());
            priceSnapshotRepository.save(snapshot);
        });
    }

    public Optional<PriceSnapshot> getLatest(String symbol) {
        return priceSnapshotRepository.findTopBySymbolOrderByFetchedAtDesc(SymbolNormalizer.normalize(symbol));
    }
}
