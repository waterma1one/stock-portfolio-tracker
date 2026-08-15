package com.portfoliotracker.symbolprofile;

import java.util.Optional;

public interface SymbolProfileProvider {
    Optional<SymbolProfileData> getProfile(String symbol);
}
