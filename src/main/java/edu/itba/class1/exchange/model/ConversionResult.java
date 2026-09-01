package edu.itba.class1.exchange.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;

public record ConversionResult(Currency fromCurrency, MoneyAmount amount, Instant fetchedAt, LocalDate rateDate,
								Map<Currency, ConversionDetail> conversions) {

	public ConversionResult {
		conversions = Collections.unmodifiableMap(new LinkedHashMap<>(conversions));
	}

	public boolean isHistorical() {
		return rateDate != null;
	}
}
