package edu.itba.class1.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/**
 * Port through which the business rules ({@link CurrencyConverter}) obtain currency data,
 * without knowing anything about how that data is actually fetched (HTTP, JSON, which
 * external API, etc). {@link CurrencyConverter} depends only on this interface; the concrete
 * detail talking to freecurrencyapi.com ({@link FreeCurrencyApiExchangeRateProvider}) lives
 * behind it, following the dependency-inversion example reviewed in class.
 * <p>
 * This grew from the single-pair {@code getExchangeRate(from, to)} used in class into a
 * batch-oriented contract, so one call can price several target currencies at once (TP1
 * user stories 1, 5 and 6) instead of the caller looping and issuing one request per currency.
 */
public interface ExchangeRateProvider {

	/**
	 * @return every currency the provider supports.
	 */
	List<Currency> listSupportedCurrencies();

	/**
	 * @param fromCurrency  the currency to convert from.
	 * @param toCurrencies  the currencies to obtain a rate for.
	 * @return the most recent exchange rates available, keyed by target currency.
	 */
	Map<Currency, BigDecimal> getExchangeRates(Currency fromCurrency, List<Currency> toCurrencies);

	/**
	 * @param fromCurrency the currency to convert from.
	 * @param toCurrencies the currencies to obtain a rate for.
	 * @param date         the past date to look up end-of-day rates for.
	 * @return the exchange rates that applied on {@code date}, keyed by target currency.
	 */
	Map<Currency, BigDecimal> getHistoricalExchangeRates(Currency fromCurrency, List<Currency> toCurrencies, LocalDate date);
}
