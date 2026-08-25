package edu.itba.class1.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Currency conversion business rules.
 * <p>
 * This class only knows about {@link ExchangeRateProvider}: it has no idea whether rates come
 * from an HTTP API, a file, or a database. That detail is injected, so the business rules can
 * be unit tested without any network access.
 */
public class CurrencyConverter {

	private final ExchangeRateProvider exchangeRateProvider;

	public CurrencyConverter(final ExchangeRateProvider exchangeRateProvider) {
		this.exchangeRateProvider = Objects.requireNonNull(exchangeRateProvider,
				"exchangeRateProvider must not be null");
	}

	/**
	 * User story 1: lists every currency supported by the provider, so the caller knows which
	 * ones it can use.
	 */
	public List<Currency> listSupportedCurrencies() {
		return exchangeRateProvider.listSupportedCurrencies();
	}

	/**
	 * User stories 2 and 3: the current exchange rate between two currencies and when it was
	 * obtained, without converting any amount.
	 */
	public ExchangeRate getExchangeRate(final Currency fromCurrency, final Currency toCurrency) {
		Objects.requireNonNull(fromCurrency, "fromCurrency must not be null");
		Objects.requireNonNull(toCurrency, "toCurrency must not be null");

		final Map<Currency, BigDecimal> rates = exchangeRateProvider.getExchangeRates(fromCurrency, List.of(toCurrency));
		final BigDecimal rate = rateFor(rates, toCurrency);
		return new ExchangeRate(fromCurrency, toCurrency, rate, Instant.now());
	}

	/**
	 * Converts {@code amount} of {@code fromCurrency} into {@code toCurrency} using the most
	 * recent rate available. This is the original single-pair signature reviewed in class,
	 * kept for backward compatibility and implemented in terms of the new multi-currency
	 * {@link #convert(Currency, MoneyAmount, List)}.
	 * <p>
	 * Unlike the version reviewed in class, provider failures are no longer swallowed: they
	 * propagate as a {@link CurrencyExchangeException} instead of silently returning
	 * {@link MoneyAmount#ZERO} (user story 4).
	 */
	public MoneyAmount convert(final Currency fromCurrency, final Currency toCurrency, final MoneyAmount amount) {
		Objects.requireNonNull(toCurrency, "toCurrency must not be null");
		return convert(fromCurrency, amount, List.of(toCurrency)).conversions().get(toCurrency).convertedAmount();
	}

	/**
	 * User story 5: converts {@code amount} of {@code fromCurrency} into every currency in
	 * {@code toCurrencies} at once, using the most recent rates available.
	 */
	public ConversionResult convert(final Currency fromCurrency, final MoneyAmount amount, final List<Currency> toCurrencies) {
		Objects.requireNonNull(fromCurrency, "fromCurrency must not be null");
		Objects.requireNonNull(amount, "amount must not be null");
		Objects.requireNonNull(toCurrencies, "toCurrencies must not be null");

		final Map<Currency, BigDecimal> rates = exchangeRateProvider.getExchangeRates(fromCurrency, toCurrencies);
		return buildConversionResult(fromCurrency, amount, toCurrencies, rates, null);
	}

	/**
	 * User story 6: converts {@code amount} of {@code fromCurrency} into every currency in
	 * {@code toCurrencies}, using the end-of-day rates that applied on {@code date}.
	 */
	public ConversionResult convert(final Currency fromCurrency, final MoneyAmount amount,
									 final List<Currency> toCurrencies, final LocalDate date) {
		Objects.requireNonNull(fromCurrency, "fromCurrency must not be null");
		Objects.requireNonNull(amount, "amount must not be null");
		Objects.requireNonNull(toCurrencies, "toCurrencies must not be null");
		Objects.requireNonNull(date, "date must not be null");

		final Map<Currency, BigDecimal> rates = exchangeRateProvider.getHistoricalExchangeRates(fromCurrency, toCurrencies, date);
		return buildConversionResult(fromCurrency, amount, toCurrencies, rates, date);
	}

	private ConversionResult buildConversionResult(final Currency fromCurrency, final MoneyAmount amount,
													 final List<Currency> toCurrencies, final Map<Currency, BigDecimal> rates,
													 final LocalDate rateDate) {
		final Map<Currency, ConversionDetail> conversions = new LinkedHashMap<>();
		for (final Currency toCurrency : toCurrencies) {
			final BigDecimal rate = rateFor(rates, toCurrency);
			conversions.put(toCurrency, new ConversionDetail(rate, amount.multiply(rate)));
		}
		return new ConversionResult(fromCurrency, amount, Instant.now(), rateDate, conversions);
	}

	private BigDecimal rateFor(final Map<Currency, BigDecimal> rates, final Currency currency) {
		final BigDecimal rate = rates.get(currency);
		if (rate == null) {
			throw new UnknownCurrencyException(currency);
		}
		return rate;
	}
}
