package edu.itba.class1.exchange.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

public record ExchangeRate(Currency fromCurrency, Currency toCurrency, BigDecimal rate, Instant fetchedAt) {


}
