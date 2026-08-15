package com.portfoliotracker.symbolprofile;

import com.portfoliotracker.common.SymbolNormalizer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "symbol_profile")
@Getter
@Setter
@NoArgsConstructor
public class SymbolProfile {

    @Id
    @Column(length = 20)
    private String symbol;

    @Column(nullable = true)
    private String sector;

    @Column(nullable = false)
    private String name;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    /**
     * Defense-in-depth: SymbolProfileService already normalizes before saving, but any
     * future direct save bypassing the service must not be able to write a non-canonical
     * symbol either. This is especially critical since symbol is the primary key — without
     * normalization, saving "AAPL" and "aapl" would silently create two separate rows.
     */
    @PrePersist
    @PreUpdate
    private void normalizeSymbol() {
        symbol = SymbolNormalizer.normalize(symbol);
    }
}
