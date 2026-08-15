package com.portfoliotracker.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolNormalizerTest {

    private final Locale originalDefault = Locale.getDefault();

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(originalDefault);
    }

    @Test
    void trimsAndUppercases() {
        assertThat(SymbolNormalizer.normalize("  aapl  ")).isEqualTo("AAPL");
    }

    @Test
    void passesThroughNull() {
        assertThat(SymbolNormalizer.normalize(null)).isNull();
    }

    /**
     * String.toUpperCase() with no Locale argument uses the JVM default locale. Under a
     * Turkish/Azeri default locale, lowercase 'i' uppercases to dotted 'İ' (U+0130), not 'I' --
     * a real ticker like "intc" would normalize to "İNTC" and then fail the symbol format
     * regex, rejecting a legitimate BUY/SELL request. normalize() must be immune to this.
     */
    @Test
    void isImmuneToTurkishDefaultLocaleDottedI() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        assertThat(SymbolNormalizer.normalize("intc")).isEqualTo("INTC");
    }
}
