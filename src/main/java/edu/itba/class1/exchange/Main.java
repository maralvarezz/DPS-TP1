package edu.itba.class1.exchange;

import edu.itba.class1.exchange.exception.CurrencyExchangeException;
import edu.itba.class1.exchange.model.ConversionResult;
import edu.itba.class1.exchange.model.ExchangeRate;
import edu.itba.class1.exchange.model.MoneyAmount;
import edu.itba.class1.exchange.provider.FreeCurrencyApiExchangeRateProvider;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public final class Main {

	private static final String API_BASE_URL = "https://api.freecurrencyapi.com/v1";
	private static final String API_KEY = "fca_live_tMQ4oYRmk8T587mrTdOFbTREYXjqCLRkXwJUS4C6";
	private static final PrintStream OUT = new PrintStream(System.out, true, StandardCharsets.UTF_8);
	private static final PrintStream ERR = new PrintStream(System.err, true, StandardCharsets.UTF_8);
	private static final Locale CURRENCY_DISPLAY_LOCALE = Locale.US;
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
			.ofPattern("uuuu-MM-dd HH:mm:ss VV")
			.withZone(ZoneId.systemDefault());
	private static final MoneyAmount DEFAULT_AMOUNT = new MoneyAmount(new BigDecimal("100"));
	private static final Currency DEFAULT_FROM_CURRENCY = Currency.getInstance("USD");
	private static final List<Currency> DEFAULT_TO_CURRENCIES = List.of(
			Currency.getInstance("EUR"),
			Currency.getInstance("JPY"));
	private static final LocalDate DEFAULT_HISTORICAL_DATE = LocalDate.of(2024, 11, 20);

	public static void main(final String[] args) {
		try {
			final DemoOptions options = parseArguments(args);
			final var exchangeRateProvider = new FreeCurrencyApiExchangeRateProvider(API_BASE_URL, API_KEY);
			final var currencyConverter = new CurrencyConverter(exchangeRateProvider);
			runDemo(currencyConverter, options);
		} catch (final IllegalArgumentException e) {
			ERR.println("Invalid arguments: " + e.getMessage());
			printUsage();
		} catch (final CurrencyExchangeException e) {
			ERR.println("The currency exchange operation failed: " + e.getMessage());
		}
	}

	private static void runDemo(final CurrencyConverter currencyConverter, final DemoOptions options) {
		printSupportedCurrencies(currencyConverter);
		printExchangeRate(currencyConverter, options.fromCurrency(), options.toCurrencies().getFirst());
		printConversionToMultipleCurrencies(currencyConverter, options.fromCurrency(), options.amount(),
				options.toCurrencies());
		printHistoricalConversion(currencyConverter, options.fromCurrency(), options.amount(),
				options.toCurrencies(), options.historicalDate());
	}

	private static void printSupportedCurrencies(final CurrencyConverter currencyConverter) {
		OUT.println("== Supported currencies ==");
		final List<Currency> currencies = currencyConverter.listSupportedCurrencies();
		currencies.forEach(currency ->
				OUT.println(currency.getCurrencyCode() + " (" + currency.getSymbol(CURRENCY_DISPLAY_LOCALE)
						+ ") - " + currency.getDisplayName(CURRENCY_DISPLAY_LOCALE)));
	}

	private static void printExchangeRate(final CurrencyConverter currencyConverter, final Currency from, final Currency to) {
		OUT.println("\n== Rate only (no amount) ==");
		final ExchangeRate quote = currencyConverter.getExchangeRate(from, to);
		OUT.println("1 " + quote.fromCurrency().getCurrencyCode() + " = " + quote.rate() + " "
				+ quote.toCurrency().getCurrencyCode() + " (as of " + formatTimestamp(quote.fetchedAt()) + ")");
	}

	private static void printConversionToMultipleCurrencies(final CurrencyConverter currencyConverter,
			final Currency from, final MoneyAmount amount, final List<Currency> to) {
		OUT.println("\n== Convert " + amount.amount() + " " + from.getCurrencyCode()
				+ " to " + currencyCodes(to) + " ==");
		final ConversionResult result = currencyConverter.convert(from, amount, to);
		printConversionResult(result);
	}

	private static void printHistoricalConversion(final CurrencyConverter currencyConverter, final Currency from,
			final MoneyAmount amount, final List<Currency> to, final LocalDate date) {
		OUT.println("\n== Historical: " + amount.amount() + " " + from.getCurrencyCode()
				+ " to " + currencyCodes(to) + " on " + date + " ==");
		final ConversionResult result = currencyConverter.convert(from, amount, to, date);
		printConversionResult(result);
	}

	private static void printConversionResult(final ConversionResult result) {
		OUT.println("Quote fetched at: " + formatTimestamp(result.fetchedAt())
				+ (result.isHistorical() ? " (rates for " + result.rateDate() + ")" : " (latest rates)"));
		result.conversions().forEach((currency, detail) ->
				OUT.println(result.amount().amount() + " " + result.fromCurrency().getCurrencyCode() + " = "
						+ detail.convertedAmount().amount() + " " + currency.getCurrencyCode()
						+ " (rate used: " + detail.rate() + ")"));
	}

	private static String formatTimestamp(final Instant timestamp) {
		return TIMESTAMP_FORMATTER.format(timestamp);
	}

	private static DemoOptions parseArguments(final String[] args) {
		MoneyAmount amount = DEFAULT_AMOUNT;
		Currency fromCurrency = DEFAULT_FROM_CURRENCY;
		List<Currency> toCurrencies = DEFAULT_TO_CURRENCIES;
		LocalDate historicalDate = DEFAULT_HISTORICAL_DATE;

		for (final String argument : args) {
			final int separatorIndex = argument.indexOf('=');
			if (!argument.startsWith("--") || separatorIndex < 3 || separatorIndex == argument.length() - 1) {
				throw new IllegalArgumentException("expected --name=value but received '" + argument + "'");
			}

			final String name = argument.substring(2, separatorIndex);
			final String value = argument.substring(separatorIndex + 1);
			switch (name) {
				case "amount" -> amount = parseAmount(value);
				case "from" -> fromCurrency = parseCurrency(value, "--from");
				case "to" -> toCurrencies = parseTargetCurrencies(value);
				case "date" -> historicalDate = parseDate(value);
				default -> throw new IllegalArgumentException("unknown option '--" + name + "'");
			}
		}

		return new DemoOptions(amount, fromCurrency, toCurrencies, historicalDate);
	}

	private static MoneyAmount parseAmount(final String value) {
		try {
			return new MoneyAmount(new BigDecimal(value));
		} catch (final NumberFormatException e) {
			throw new IllegalArgumentException("--amount must be a number", e);
		}
	}

	private static List<Currency> parseTargetCurrencies(final String value) {
		return Arrays.stream(value.split(",", -1))
				.map(String::trim)
				.map(code -> parseCurrency(code, "--to"))
				.distinct()
				.toList();
	}

	private static Currency parseCurrency(final String value, final String optionName) {
		try {
			return Currency.getInstance(value.trim().toUpperCase(Locale.ROOT));
		} catch (final IllegalArgumentException e) {
			throw new IllegalArgumentException(optionName + " must contain a valid ISO 4217 currency code", e);
		}
	}

	private static LocalDate parseDate(final String value) {
		try {
			return LocalDate.parse(value);
		} catch (final DateTimeParseException e) {
			throw new IllegalArgumentException("--date must use the YYYY-MM-DD format", e);
		}
	}

	private static String currencyCodes(final List<Currency> currencies) {
		return String.join(", ", currencies.stream().map(Currency::getCurrencyCode).toList());
	}

	private static void printUsage() {
		ERR.println("Usage: --amount=<number> --from=<currency> --to=<currency,...> --date=<YYYY-MM-DD>");
		ERR.println("Every option is optional. Defaults: --amount=100 --from=USD --to=EUR,JPY --date=2024-11-20");
	}

	private record DemoOptions(MoneyAmount amount, Currency fromCurrency, List<Currency> toCurrencies,
			LocalDate historicalDate) {
	}
}
