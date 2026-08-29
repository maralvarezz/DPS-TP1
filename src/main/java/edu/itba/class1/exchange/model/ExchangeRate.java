package edu.itba.class1.exchange.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * The exchange rate between two currencies, without applying it to any amount
 * (user story 3: "quiero poder obtener solo la cotizacion entre dos monedas").
 *
 * @param fromCurrency the base currency
 * @param toCurrency   the target currency
 * @param rate         units of {@code toCurrency} per unit of {@code fromCurrency}
 * @param fetchedAt    when this quote was obtained (user story 2)
 */
public record ExchangeRate(Currency fromCurrency, Currency toCurrency, BigDecimal rate, Instant fetchedAt) {


}
