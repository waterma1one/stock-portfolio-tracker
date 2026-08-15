package com.portfoliotracker.symbolprofile;

import com.portfoliotracker.common.SymbolNormalizer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class SymbolProfileService {

    private final SymbolProfileProvider symbolProfileProvider;
    private final SymbolProfileRepository symbolProfileRepository;

    public SymbolProfileService(SymbolProfileProvider symbolProfileProvider, SymbolProfileRepository symbolProfileRepository) {
        this.symbolProfileProvider = symbolProfileProvider;
        this.symbolProfileRepository = symbolProfileRepository;
    }

    public void ensureCached(String symbol) {
        String normalized = SymbolNormalizer.normalize(symbol);
        if (symbolProfileRepository.findBySymbol(normalized).isPresent()) {
            return;
        }
        symbolProfileProvider.getProfile(normalized).ifPresent(data -> {
            SymbolProfile profile = new SymbolProfile();
            profile.setSymbol(normalized);
            profile.setSector(data.sector());
            profile.setName(data.name());
            profile.setFetchedAt(Instant.now());
            symbolProfileRepository.save(profile);
        });
    }

    public Optional<SymbolProfile> getProfile(String symbol) {
        return symbolProfileRepository.findBySymbol(SymbolNormalizer.normalize(symbol));
    }
}
