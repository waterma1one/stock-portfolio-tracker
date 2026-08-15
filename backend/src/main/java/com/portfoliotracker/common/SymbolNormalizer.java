package com.portfoliotracker.common;

import java.util.Locale;

public class SymbolNormalizer {

    public static String normalize(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
