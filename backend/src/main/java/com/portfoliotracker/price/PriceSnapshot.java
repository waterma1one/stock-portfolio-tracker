package com.portfoliotracker.price;

import com.portfoliotracker.common.SymbolNormalizer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    /**
     * Defense-in-depth: PriceService.fetchAndStore already normalizes before saving, but any
     * future direct save bypassing the service must not be able to write a non-canonical
     * symbol either.
     */
    @PrePersist
    @PreUpdate
    private void normalizeSymbol() {
        symbol = SymbolNormalizer.normalize(symbol);
    }
}
