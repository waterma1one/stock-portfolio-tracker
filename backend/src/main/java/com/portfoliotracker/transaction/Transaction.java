package com.portfoliotracker.transaction;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    /** Convenience factory for building transient test fixtures without portfolioId/id. */
    public static Transaction of(String symbol, TransactionType type, BigDecimal quantity,
                                  BigDecimal price, Instant executedAt) {
        Transaction t = new Transaction();
        t.setSymbol(symbol);
        t.setType(type);
        t.setQuantity(quantity);
        t.setPrice(price);
        t.setExecutedAt(executedAt);
        return t;
    }
}
