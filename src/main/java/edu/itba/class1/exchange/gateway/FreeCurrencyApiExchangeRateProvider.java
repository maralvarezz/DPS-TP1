package edu.itba.class1.exchange.gateway;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;

import edu.itba.class1.exchange.domain.exception.CurrencyExchangeApiException;
import edu.itba.class1.exchange.domain.exception.CurrencyExchangeConnectionException;
import edu.itba.class1.exchange.domain.interfaces.ExchangeRateProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class FreeCurrencyApiExchangeRateProvider implements ExchangeRateProvider {

	private final String apiBaseUrl;
	private final String apiKey;
	private final Gson gson = new Gson();

	public FreeCurrencyApiExchangeRateProvider(final String apiBaseUrl, final String apiKey) {
		this.apiBaseUrl = requireNonBlank(apiBaseUrl, "apiBaseUrl");
		this.apiKey = requireNonBlank(apiKey, "apiKey");
	}

	@Override
	public List<Currency> listSupportedCurrencies() {
		final String body = get(apiBaseUrl + "/currencies", Map.of());
		final CurrenciesResponse response = parse(body, CurrenciesResponse.class);
		final Map<String, Object> data = requireData(response.data);
		return data.keySet().stream()
				.map(this::toJavaCurrencyOrNull)
				.filter(Objects::nonNull)
				.toList();
	}

	@Override
	public Map<Currency, BigDecimal> getExchangeRates(final Currency fromCurrency, final List<Currency> toCurrencies) {
		final List<Currency> copyToCurrencies = List.copyOf(toCurrencies);

		final String body = get(apiBaseUrl + "/latest", queryParams(fromCurrency, copyToCurrencies));
		final RatesResponse response = parse(body, RatesResponse.class);
		return toJavaCurrencyRates(requireData(response.data));
	}

	@Override
	public Map<Currency, BigDecimal> getHistoricalExchangeRates(final Currency fromCurrency,
															  final List<Currency> toCurrencies,
															  final LocalDate date) {
		final List<Currency> copyToCurrencies = List.copyOf(toCurrencies);
		final Map<String, String> params = queryParams(fromCurrency, copyToCurrencies);
		params.put("date", date.toString());

		final String body = get(apiBaseUrl + "/historical", params);
		final HistoricalRatesResponse response = parse(body, HistoricalRatesResponse.class);
		final Map<String, Map<String, BigDecimal>> data = requireData(response.data);
		final Map<String, BigDecimal> ratesForDate = data.get(date.toString());
		return ratesForDate == null ? Map.of() : toJavaCurrencyRates(ratesForDate);
	}

	private static String requireNonBlank(final String value, final String name) {
		Objects.requireNonNull(value, name + " must not be null");
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
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
			if (rate == null || rate.signum() <= 0) {
				throw new CurrencyExchangeApiException(200, "invalid_response",
						"The currency exchange API returned a non-positive or null rate for currency '" + code + "'.");
			}
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
			return null;
		}
	}

	private String get(final String url, final Map<String, String> queryParams) {
		try {
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

	private <T> T requireData(final T data) {
		if (data == null) {
			throw new CurrencyExchangeApiException(200, "empty_response",
					"The currency exchange API returned a response without a \"data\" field.");
		}
		return data;
	}

	private ApiErrorResponse tryParseError(final String body) {
		try {
			return gson.fromJson(body, ApiErrorResponse.class);
		} catch (final JsonSyntaxException e) {
			return null;
		}
	}

	private static class RatesResponse {
		private Map<String, BigDecimal> data;
	}

	private static class HistoricalRatesResponse {
		private Map<String, Map<String, BigDecimal>> data;
	}

	private static class CurrenciesResponse {
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
