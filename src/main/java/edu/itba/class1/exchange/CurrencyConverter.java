package edu.itba.class1.exchange;

import edu.itba.class1.exchange.exception.UnknownCurrencyException;
import edu.itba.class1.exchange.model.ConversionDetail;
import edu.itba.class1.exchange.model.ConversionResult;
import edu.itba.class1.exchange.model.ExchangeRate;
import edu.itba.class1.exchange.model.MoneyAmount;
import edu.itba.class1.exchange.provider.ExchangeRateProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CurrencyConverter {

	private final ExchangeRateProvider exchangeRateProvider;

	public CurrencyConverter(final ExchangeRateProvider exchangeRateProvider) {
		this.exchangeRateProvider = Objects.requireNonNull(exchangeRateProvider, "exchangeRateProvider must not be null");
	}

	public List<Currency> listSupportedCurrencies() {
		final List<Currency> currencies = Objects.requireNonNull(exchangeRateProvider.listSupportedCurrencies(),
				"exchangeRateProvider must not return a null currency list");
		return List.copyOf(currencies);
	}

	public ExchangeRate getExchangeRate(final Currency fromCurrency, final Currency toCurrency) {
		Objects.requireNonNull(fromCurrency, "fromCurrency must not be null");
		Objects.requireNonNull(toCurrency, "toCurrency must not be null");

		final Map<Currency, BigDecimal> rates = requireRates(exchangeRateProvider.getExchangeRates(fromCurrency, List.of(toCurrency)));
		final BigDecimal rate = rateFor(rates, toCurrency);
		return new ExchangeRate(fromCurrency, toCurrency, rate, Instant.now());
	}

	public MoneyAmount convert(final Currency fromCurrency, final Currency toCurrency, final MoneyAmount amount) {
		return convert(fromCurrency, amount, List.of(toCurrency)).conversions().get(toCurrency).convertedAmount();
	}

	public ConversionResult convert(final Currency fromCurrency, final MoneyAmount amount, final List<Currency> toCurrencies) {
		Objects.requireNonNull(fromCurrency, "fromCurrency must not be null");
		Objects.requireNonNull(amount, "amount must not be null");
		final List<Currency> validatedToCurrencies = requireTargetCurrencies(toCurrencies);

		final Map<Currency, BigDecimal> rates = requireRates(
				exchangeRateProvider.getExchangeRates(fromCurrency, validatedToCurrencies));
		return buildConversionResult(fromCurrency, amount, validatedToCurrencies, rates, null);
	}

	public ConversionResult convert(final Currency fromCurrency, final MoneyAmount amount,
									 final List<Currency> toCurrencies, final LocalDate date) {
		Objects.requireNonNull(fromCurrency, "fromCurrency must not be null");
		Objects.requireNonNull(amount, "amount must not be null");
		final List<Currency> validatedToCurrencies = requireTargetCurrencies(toCurrencies);
		Objects.requireNonNull(date, "date must not be null");

		final Map<Currency, BigDecimal> rates = requireRates(
				exchangeRateProvider.getHistoricalExchangeRates(fromCurrency, validatedToCurrencies, date));
		return buildConversionResult(fromCurrency, amount, validatedToCurrencies, rates, date);
	}

	private List<Currency> requireTargetCurrencies(final List<Currency> toCurrencies) {
		if (toCurrencies.isEmpty()) {
			throw new IllegalArgumentException("toCurrencies must not be empty");
		}
		return List.copyOf(toCurrencies);
	}

	private Map<Currency, BigDecimal> requireRates(final Map<Currency, BigDecimal> rates) {
		Objects.requireNonNull(rates, "exchangeRateProvider must not return null rates");
		rates.forEach((currency, rate) -> {
			Objects.requireNonNull(currency, "exchangeRateProvider must not return a null currency");
			Objects.requireNonNull(rate, "exchangeRateProvider must not return a null rate");
			if (rate.signum() <= 0) {
				throw new IllegalStateException("exchangeRateProvider must return positive rates");
			}
		});
		return Map.copyOf(rates);
	}

	private ConversionResult buildConversionResult(final Currency fromCurrency, final MoneyAmount amount,
													 final List<Currency> toCurrencies, final Map<Currency, BigDecimal> rates,
													 final LocalDate rateDate) {
		final Map<Currency, ConversionDetail> conversions = new LinkedHashMap<>();
		toCurrencies.forEach(toCurrency -> {
            final BigDecimal rate = rateFor(rates, toCurrency);
            conversions.put(toCurrency,new ConversionDetail(rate, amount.multiply(rate)));
        });
		return new ConversionResult(fromCurrency, amount, Instant.now(), rateDate, conversions);
	}

	private BigDecimal rateFor(final Map<Currency, BigDecimal> rates, final Currency currency) {
		return Optional.ofNullable(rates.get(currency)).orElseThrow(()->new UnknownCurrencyException(currency));
	}
}
