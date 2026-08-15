package com.portfoliotracker.pricehistory;

import java.time.LocalDate;
import java.util.List;

public interface CandleProvider {
    List<DailyClose> getDailyCloses(String symbol, LocalDate from, LocalDate to);
}
