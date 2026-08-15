package com.portfoliotracker.holding.dto;

import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.price.PriceSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HoldingResponseTest {

    /**
     * Multiplying two scale-4 BigDecimals yields scale 8 unless explicitly rounded back down
     * (e.g. 313.3300 * 10.0000 = 3133.30000000). isEqualByComparingTo() in the integration
     * tests ignores scale entirely, which is exactly how this went unnoticed -- so this test
     * asserts on scale directly, not just numeric value.
     */
    @Test
    void marketValueAndUnrealizedPnlAreScaledToFourDecimalPlacesNotEight() {
        Holding holding = new Holding("AAPL", new BigDecimal("10.0000"), new BigDecimal("150.0000"));
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setSymbol("AAPL");
        snapshot.setPrice(new BigDecimal("313.3300"));
        snapshot.setFetchedAt(Instant.now());

        HoldingResponse response = HoldingResponse.from(holding, Optional.of(snapshot));

        assertThat(response.marketValue().scale()).isEqualTo(4);
        assertThat(response.unrealizedPnl().scale()).isEqualTo(4);
    }

    @Test
    void marketValueAndUnrealizedPnlHaveCorrectValues() {
        Holding holding = new Holding("AAPL", new BigDecimal("10.0000"), new BigDecimal("150.0000"));
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setSymbol("AAPL");
        snapshot.setPrice(new BigDecimal("313.3300"));
        snapshot.setFetchedAt(Instant.now());

        HoldingResponse response = HoldingResponse.from(holding, Optional.of(snapshot));

        assertThat(response.marketValue()).isEqualByComparingTo("3133.3000");
        assertThat(response.unrealizedPnl()).isEqualByComparingTo("1633.3000");
    }

    @Test
    void valuationFieldsAreNullWhenNoPriceSnapshotExists() {
        Holding holding = new Holding("ZZZZ", new BigDecimal("5.0000"), new BigDecimal("10.0000"));

        HoldingResponse response = HoldingResponse.from(holding, Optional.empty());

        assertThat(response.currentPrice()).isNull();
        assertThat(response.marketValue()).isNull();
        assertThat(response.unrealizedPnl()).isNull();
    }
}
