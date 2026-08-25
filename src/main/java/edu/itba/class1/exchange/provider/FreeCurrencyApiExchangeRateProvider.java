package edu.itba.class1.exchange.provider;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;

import edu.itba.class1.exchange.CurrencyConverter;
import edu.itba.class1.exchange.exception.CurrencyExchangeApiException;
import edu.itba.class1.exchange.exception.CurrencyExchangeConnectionException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link ExchangeRateProvider} backed by the <a href="https://freecurrencyapi.com/">freecurrencyapi.com</a>
 * REST API, using Unirest and Gson exactly as reviewed in class. This is the only class in the
 * project allowed to know about HTTP or JSON: everything above it (the business rules in
 * {@link CurrencyConverter}) talks to the {@link ExchangeRateProvider} abstraction instead.
 */
public class FreeCurrencyApiExchangeRateProvider implements ExchangeRateProvider {

	private final String apiBaseUrl;
	private final String apiKey;
	private final Gson gson = new Gson();

	/**
	 * @param apiBaseUrl the API's base URL, e.g. {@code https://api.freecurrencyapi.com/v1}
	 *                   (without a trailing slash). The {@code /latest}, {@code /historical}
	 *                   and {@code /currencies} endpoints are appended to it.
	 * @param apiKey     the freecurrencyapi.com API key.
	 */
	public FreeCurrencyApiExchangeRateProvider(final String apiBaseUrl, final String apiKey) {
		this.apiBaseUrl = Objects.requireNonNull(apiBaseUrl, "apiBaseUrl must not be null");
		this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
	}

	@Override
	public List<Currency> listSupportedCurrencies() {
		final String body = get(apiBaseUrl + "/currencies", Map.of());
		final CurrenciesResponse response = parse(body, CurrenciesResponse.class);
		return response.data.keySet().stream()
				.map(this::toJavaCurrencyOrNull)
				.filter(Objects::nonNull)
				.toList();
	}

	@Override
	public Map<Currency, BigDecimal> getExchangeRates(final Currency fromCurrency, final List<Currency> toCurrencies) {
		final String body = get(apiBaseUrl + "/latest", queryParams(fromCurrency, toCurrencies));
		final RatesResponse response = parse(body, RatesResponse.class);
		return toJavaCurrencyRates(response.data);
	}

	@Override
	public Map<Currency, BigDecimal> getHistoricalExchangeRates(final Currency fromCurrency,
																  final List<Currency> toCurrencies,
																  final LocalDate date) {
		final Map<String, String> params = queryParams(fromCurrency, toCurrencies);
		params.put("date", date.toString());

		final String body = get(apiBaseUrl + "/historical", params);
		final HistoricalRatesResponse response = parse(body, HistoricalRatesResponse.class);
		final Map<String, BigDecimal> ratesForDate = response.data.getOrDefault(date.toString(), Map.of());
		return toJavaCurrencyRates(ratesForDate);
	}

	private Map<String, String> queryParams(final Currency fromCurrency, final List<Currency> toCurrencies) {
		final Map<String, String> params = new LinkedHashMap<>();
		params.put("base_currency", fromCurrency.getCurrencyCode());
		if (!toCurrencies.isEmpty()) {
			params.put("currencies", toCurrencies.stream().map(Currency::getCurrencyCode)
					.collect(Collectors.joining(",")));
		}
		return params;
	}

	private Map<Currency, BigDecimal> toJavaCurrencyRates(final Map<String, BigDecimal> ratesByCode) {
		final Map<Currency, BigDecimal> rates = new LinkedHashMap<>();
		ratesByCode.forEach((code, rate) -> {
			final Currency currency = toJavaCurrencyOrNull(code);
			if (currency != null) {
				rates.put(currency, rate);
			}
		});
		return rates;
	}

	private Currency toJavaCurrencyOrNull(final String currencyCode) {
		try {
			return Currency.getInstance(currencyCode);
		} catch (final IllegalArgumentException e) {
			// The JVM's ISO 4217 table does not know this code: skip it rather than fail the
			// whole request over one currency we cannot represent.
			return null;
		}
	}

	private String get(final String url, final Map<String, String> queryParams) {
		try {
			// Unirest's fluent methods don't all return the same static type (header() narrows to
			// GetRequest, queryString() widens back to HttpRequest), so the loop below is typed
			// against the common HttpRequest interface rather than GetRequest.
			HttpRequest request = Unirest.get(url).header("accept", "application/json").header("apikey", apiKey);
			for (final Map.Entry<String, String> param : queryParams.entrySet()) {
				request = request.queryString(param.getKey(), param.getValue());
			}

			final var response = request.asString();
			if (response.getStatus() != 200) {
				throw toApiException(response.getStatus(), response.getBody());
			}
			return response.getBody();
		} catch (final UnirestException e) {
			throw new CurrencyExchangeConnectionException("Could not reach the currency exchange API: " + e.getMessage(), e);
		}
	}

	private <T> T parse(final String body, final Class<T> type) {
		try {
			final T parsed = gson.fromJson(body, type);
			if (parsed == null) {
				throw new CurrencyExchangeApiException(200, "empty_response",
						"The currency exchange API returned an empty response body.");
			}
			return parsed;
		} catch (final JsonSyntaxException e) {
			throw new CurrencyExchangeApiException(200, "invalid_json",
					"The currency exchange API returned a response that could not be parsed: " + e.getMessage());
		}
	}

	private CurrencyExchangeApiException toApiException(final int statusCode, final String body) {
		final ApiErrorResponse errorResponse = tryParseError(body);
		final String message = errorResponse != null && errorResponse.message != null
				? errorResponse.message
				: "The currency exchange API returned HTTP " + statusCode + ".";
		final String errorCode = errorResponse != null && errorResponse.error != null ? errorResponse.error.code : null;
		return new CurrencyExchangeApiException(statusCode, errorCode, message);
	}

	private ApiErrorResponse tryParseError(final String body) {
		try {
			return gson.fromJson(body, ApiErrorResponse.class);
		} catch (final JsonSyntaxException e) {
			return null;
		}
	}

	// --- Response bodies, mapped the same way ExchangeRateResponse was mapped in class ---

	private static class RatesResponse {
		private Map<String, BigDecimal> data;
	}

	private static class HistoricalRatesResponse {
		private Map<String, Map<String, BigDecimal>> data;
	}

	private static class CurrenciesResponse {
		// We only need to know which currency codes exist; java.util.Currency already supplies
		// a display name and symbol for each one, so the metadata values are left untyped.
		private Map<String, Object> data;
	}

	private static class ApiErrorResponse {
		private String message;
		private ApiErrorDetail error;
	}

	private static class ApiErrorDetail {
		private String code;
		private String message;
	}
}
