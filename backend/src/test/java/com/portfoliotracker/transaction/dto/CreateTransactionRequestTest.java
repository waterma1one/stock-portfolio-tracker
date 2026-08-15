package com.portfoliotracker.transaction.dto;

import com.portfoliotracker.transaction.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateTransactionRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private CreateTransactionRequest requestWithSymbol(String symbol) {
        return new CreateTransactionRequest(symbol, TransactionType.BUY,
                new BigDecimal("10"), new BigDecimal("100.00"), Instant.now());
    }

    @Test
    void lowercaseSymbolIsNormalizedToUppercase() {
        CreateTransactionRequest request = requestWithSymbol("aapl");

        assertThat(request.symbol()).isEqualTo("AAPL");
    }

    @Test
    void whitespacePaddedSymbolIsTrimmed() {
        CreateTransactionRequest request = requestWithSymbol("  AAPL  ");

        assertThat(request.symbol()).isEqualTo("AAPL");
    }

    @Test
    void mixedCaseSymbolWithSuffixIsNormalized() {
        CreateTransactionRequest request = requestWithSymbol(" brk.b ");

        assertThat(request.symbol()).isEqualTo("BRK.B");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AAPL", "A", "BRK.B", "BF.B", "ABCDEFGHIJ"})
    void validSymbolsPassValidation(String symbol) {
        Set<ConstraintViolation<CreateTransactionRequest>> violations =
                validator.validate(requestWithSymbol(symbol));

        assertThat(violations).isEmpty();
    }

    @Test
    void symbolExceedingMaxLengthFailsValidation() {
        String tooLong = "A".repeat(300);

        Set<ConstraintViolation<CreateTransactionRequest>> violations =
                validator.validate(requestWithSymbol(tooLong));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void symbolWithInternalWhitespaceFailsValidation() {
        Set<ConstraintViolation<CreateTransactionRequest>> violations =
                validator.validate(requestWithSymbol("AA PL"));

        assertThat(violations).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AAPL123", "AAPL!", "1234", "AA_PL"})
    void symbolWithInvalidCharactersFailsValidation(String symbol) {
        Set<ConstraintViolation<CreateTransactionRequest>> violations =
                validator.validate(requestWithSymbol(symbol));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void blankSymbolFailsValidation() {
        Set<ConstraintViolation<CreateTransactionRequest>> violations =
                validator.validate(requestWithSymbol("   "));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void nullSymbolFailsValidationWithoutThrowing() {
        Set<ConstraintViolation<CreateTransactionRequest>> violations =
                validator.validate(requestWithSymbol(null));

        assertThat(violations).isNotEmpty();
    }
}
