package com.portfoliotracker.symbolprofile;

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
        if (symbolProfileRepository.findBySymbol(symbol).isPresent()) {
            return;
        }
        symbolProfileProvider.getProfile(symbol).ifPresent(data -> {
            SymbolProfile profile = new SymbolProfile();
            profile.setSymbol(symbol);
            profile.setSector(data.sector());
            profile.setName(data.name());
            profile.setFetchedAt(Instant.now());
            symbolProfileRepository.save(profile);
        });
    }

    public Optional<SymbolProfile> getProfile(String symbol) {
        return symbolProfileRepository.findBySymbol(symbol);
    }
}
