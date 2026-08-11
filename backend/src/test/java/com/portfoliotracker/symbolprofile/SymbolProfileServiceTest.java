package com.portfoliotracker.symbolprofile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SymbolProfileServiceTest {

    @Mock
    SymbolProfileProvider symbolProfileProvider;

    @Mock
    SymbolProfileRepository symbolProfileRepository;

    @Test
    void ensureCachedSkipsFetchWhenProfileAlreadyExists() {
        SymbolProfileService service = new SymbolProfileService(symbolProfileProvider, symbolProfileRepository);
        SymbolProfile existing = new SymbolProfile();
        existing.setSymbol("AAPL");
        when(symbolProfileRepository.findBySymbol("AAPL")).thenReturn(Optional.of(existing));

        service.ensureCached("AAPL");

        verify(symbolProfileProvider, never()).getProfile(any());
    }

    @Test
    void ensureCachedFetchesAndSavesWhenProfileMissing() {
        SymbolProfileService service = new SymbolProfileService(symbolProfileProvider, symbolProfileRepository);
        when(symbolProfileRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(symbolProfileProvider.getProfile("AAPL"))
                .thenReturn(Optional.of(new SymbolProfileData("Technology", "Apple Inc")));

        service.ensureCached("AAPL");

        verify(symbolProfileRepository).save(argThatMatchesAaplProfile());
    }

    @Test
    void ensureCachedSkipsSaveWhenProviderReturnsEmpty() {
        SymbolProfileService service = new SymbolProfileService(symbolProfileProvider, symbolProfileRepository);
        when(symbolProfileRepository.findBySymbol("BADSYM")).thenReturn(Optional.empty());
        when(symbolProfileProvider.getProfile("BADSYM")).thenReturn(Optional.empty());

        service.ensureCached("BADSYM");

        verify(symbolProfileRepository, never()).save(any());
    }

    @Test
    void getProfileDelegatesToRepository() {
        SymbolProfileService service = new SymbolProfileService(symbolProfileProvider, symbolProfileRepository);
        SymbolProfile profile = new SymbolProfile();
        profile.setSymbol("AAPL");
        when(symbolProfileRepository.findBySymbol("AAPL")).thenReturn(Optional.of(profile));

        Optional<SymbolProfile> result = service.getProfile("AAPL");

        assertThat(result).contains(profile);
    }

    private SymbolProfile argThatMatchesAaplProfile() {
        return org.mockito.ArgumentMatchers.argThat(profile ->
                profile.getSymbol().equals("AAPL")
                        && profile.getSector().equals("Technology")
                        && profile.getName().equals("Apple Inc")
                        && profile.getFetchedAt() != null);
    }
}
