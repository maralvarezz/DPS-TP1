package edu.itba.class1.exchange;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

/**
 * Composition root: wires the concrete detail (freecurrencyapi.com, over Unirest) behind the
 * {@link ExchangeRateProvider} abstraction and exercises each of the 7 required user stories
 * end to end.
 * <p>
 * Requires the {@code FREECURRENCYAPI_KEY} environment variable to be set to a valid API key
 * from <a href="https://freecurrencyapi.com/">freecurrencyapi.com</a>.
 */
public final class Main {

	private static final String API_BASE_URL = "https://api.freecurrencyapi.com/v1";

	private Main() {
	}

	public static void main(final String[] args) {
		final String apiKey = System.getenv("FREECURRENCYAPI_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			System.err.println("Please set the FREECURRENCYAPI_KEY environment variable with your API key.");
			return;
		}

		final var exchangeRateProvider = new FreeCurrencyApiExchangeRateProvider(API_BASE_URL, apiKey);
		final var currencyConverter = new CurrencyConverter(exchangeRateProvider);

		try {
			runDemo(currencyConverter);
		} catch (final CurrencyExchangeException e) {
			// User story 4: connection/API errors are surfaced clearly instead of failing silently.
			System.err.println("The currency exchange operation failed: " + e.getMessage());
		}
	}

	private static void runDemo(final CurrencyConverter currencyConverter) {
		final Currency usd = Currency.getInstance("USD");
		final Currency eur = Currency.getInstance("EUR");
		final Currency jpy = Currency.getInstance("JPY");

		printSupportedCurrencies(currencyConverter);
		printExchangeRate(currencyConverter, usd, eur);
		printConversionToMultipleCurrencies(currencyConverter, usd, eur, jpy);
		printHistoricalConversion(currencyConverter, usd, eur, jpy);
	}

	private static void printSupportedCurrencies(final CurrencyConverter currencyConverter) {
		System.out.println("== Supported currencies ==");
		final List<Currency> currencies = currencyConverter.listSupportedCurrencies();
		currencies.forEach(currency ->
				System.out.println(currency.getCurrencyCode() + " (" + currency.getSymbol() + ") - " + currency.getDisplayName()));
	}

	private static void printExchangeRate(final CurrencyConverter currencyConverter, final Currency from, final Currency to) {
		System.out.println("\n== Rate only (no amount) ==");
		final ExchangeRate quote = currencyConverter.getExchangeRate(from, to);
		System.out.println("1 " + quote.fromCurrency().getCurrencyCode() + " = " + quote.rate() + " "
				+ quote.toCurrency().getCurrencyCode() + " (as of " + quote.fetchedAt() + ")");
	}

	private static void printConversionToMultipleCurrencies(final CurrencyConverter currencyConverter, final Currency from,
															  final Currency... to) {
		System.out.println("\n== Convert 100 " + from.getCurrencyCode() + " to multiple currencies ==");
		final ConversionResult result = currencyConverter.convert(from, new MoneyAmount(100), List.of(to));
		printConversionResult(result);
	}

	private static void printHistoricalConversion(final CurrencyConverter currencyConverter, final Currency from,
												   final Currency... to) {
		final LocalDate date = LocalDate.of(2024, 11, 20);
		System.out.println("\n== Historical: 100 " + from.getCurrencyCode() + " on " + date + " ==");
		final ConversionResult result = currencyConverter.convert(from, new MoneyAmount(100), List.of(to), date);
		printConversionResult(result);
	}

	private static void printConversionResult(final ConversionResult result) {
		System.out.println("Quote fetched at: " + result.fetchedAt()
				+ (result.isHistorical() ? " (rates for " + result.rateDate() + ")" : " (latest rates)"));
		result.conversions().forEach((currency, detail) ->
				System.out.println(result.amount().amount() + " " + result.fromCurrency().getCurrencyCode() + " = "
						+ detail.convertedAmount().amount() + " " + currency.getCurrencyCode()
						+ " (rate used: " + detail.rate() + ")"));
	}
}
