package edu.itba.class1.exchange.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The outcome of converting one amount, in one base currency, into one or more target
 * currencies at once (user story 5: "quiero poder convertir desde el mismo monto y moneda a
 * mas de una moneda a la vez").
 *
 * @param fromCurrency the base currency
 * @param amount       the base amount that was converted
 * @param fetchedAt    when the underlying quote was obtained, so its freshness can be verified (story 2)
 * @param rateDate     the historical date the rates apply to, or {@code null} when the latest
 *                     available rates were used (story 6)
 * @param conversions  the per-target-currency conversion detail, keyed by currency (story 7)
 */
public record ConversionResult(Currency fromCurrency, MoneyAmount amount, Instant fetchedAt, LocalDate rateDate,
								Map<Currency, ConversionDetail> conversions) {

	public ConversionResult {
		// Preserve the caller's requested order (Map.copyOf does not) so results print in the
		// same order the target currencies were requested in.
		conversions = Collections.unmodifiableMap(new LinkedHashMap<>(conversions));
	}

	public boolean isHistorical() {
		return rateDate != null;
	}
}
