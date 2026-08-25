package edu.itba.class1.exchange;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mashape.unirest.http.Unirest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Exercises {@link FreeCurrencyApiExchangeRateProvider} against a local WireMock server, so the
 * real Unirest + Gson wiring is tested end to end without depending on the actual
 * freecurrencyapi.com service or a real API key.
 */
class FreeCurrencyApiExchangeRateProviderTest {

	private static final Currency USD = Currency.getInstance("USD");
	private static final Currency EUR = Currency.getInstance("EUR");
	private static final Currency JPY = Currency.getInstance("JPY");

	private WireMockServer wireMockServer;
	private FreeCurrencyApiExchangeRateProvider provider;

	@BeforeEach
	void setUp() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		provider = new FreeCurrencyApiExchangeRateProvider(wireMockServer.baseUrl() + "/v1", "test-api-key");
	}

	@AfterEach
	void tearDown() {
		if (wireMockServer.isRunning()) {
			wireMockServer.stop();
		}
	}

	@AfterAll
	static void shutdownUnirest() throws Exception {
		Unirest.shutdown();
	}

	@Test
	void listsSupportedCurrenciesAndSkipsCodesTheJvmDoesNotRecognize() {
		wireMockServer.stubFor(get(urlPathEqualTo("/v1/currencies"))
				.withHeader("apikey", equalTo("test-api-key"))
				.willReturn(aResponse().withStatus(200).withBody("""
						{"data": {
							"EUR": {"symbol": "€", "name": "Euro", "code": "EUR"},
							"USD": {"symbol": "$", "name": "US Dollar", "code": "USD"},
							"ABC": {"symbol": "?", "name": "Not a real currency", "code": "ABC"}
						}}""")));

		final List<Currency> currencies = provider.listSupportedCurrencies();

		assertThat(currencies).containsExactlyInAnyOrder(EUR, USD);
	}

	@Test
	void getsLatestRatesWithBaseAndTargetCurrenciesAsQueryParamsAndSkipsUnrecognizedCodes() {
		wireMockServer.stubFor(get(urlPathEqualTo("/v1/latest"))
				.withQueryParam("base_currency", equalTo("USD"))
				.withQueryParam("currencies", equalTo("EUR,JPY"))
				.willReturn(aResponse().withStatus(200)
						.withBody("{\"data\": {\"EUR\": 0.9, \"JPY\": 150.0, \"ABC\": 1.0}}")));

		final Map<Currency, BigDecimal> rates = provider.getExchangeRates(USD, List.of(EUR, JPY));

		assertThat(rates).hasSize(2);
		assertThat(rates.get(EUR)).isEqualByComparingTo("0.9");
		assertThat(rates.get(JPY)).isEqualByComparingTo("150.0");
	}

	@Test
	void omittingTargetCurrenciesLeavesTheCurrenciesQueryParamOut() {
		wireMockServer.stubFor(get(urlPathEqualTo("/v1/latest"))
				.withQueryParam("base_currency", equalTo("USD"))
				.withQueryParam("currencies", absent())
				.willReturn(aResponse().withStatus(200).withBody("{\"data\": {\"EUR\": 0.9}}")));

		final Map<Currency, BigDecimal> rates = provider.getExchangeRates(USD, List.of());

		assertThat(rates.get(EUR)).isEqualByComparingTo("0.9");
	}

	@Test
	void getsHistoricalRatesForTheRequestedDate() {
		final LocalDate date = LocalDate.of(2024, 11, 20);
		wireMockServer.stubFor(get(urlPathEqualTo("/v1/historical"))
				.withQueryParam("date", equalTo("2024-11-20"))
				.willReturn(aResponse().withStatus(200).withBody("{\"data\": {\"2024-11-20\": {\"EUR\": 0.88}}}")));

		final Map<Currency, BigDecimal> rates = provider.getHistoricalExchangeRates(USD, List.of(EUR), date);

		assertThat(rates.get(EUR)).isEqualByComparingTo("0.88");
	}

	@Test
	void historicalRatesFallBackToEmptyWhenTheResponseHasNoEntryForTheRequestedDate() {
		wireMockServer.stubFor(get(urlPathEqualTo("/v1/historical"))
				.willReturn(aResponse().withStatus(200).withBody("{\"data\": {\"2024-11-19\": {\"EUR\": 0.88}}}")));

		final Map<Currency, BigDecimal> rates = provider.getHistoricalExchangeRates(USD, List.of(EUR),
				LocalDate.of(2024, 11, 20));

		assertThat(rates).isEmpty();
	}

	@Test
	void wrapsConnectionFailuresAsCurrencyExchangeConnectionException() {
		wireMockServer.stop();

		assertThatThrownBy(() -> provider.getExchangeRates(USD, List.of(EUR)))
				.isInstanceOf(CurrencyExchangeConnectionException.class);
	}

	@Test
	void mapsAFullApiErrorResponseToCurrencyExchangeApiException() {
		wireMockServer.stubFor(get(urlPathEqualTo("/v1/latest")).willReturn(aResponse().withStatus(401).withBody("""
				{"message": "Invalid authentication credentials",
				 "error": {"code": "invalid_api_key", "message": "Invalid authentication credentials"}}""")));

		final var exception = catchThrowableOfType(() -> provider.getExchangeRates(USD, List.of(EUR)),
				CurrencyExchangeApiException.class);

		assertThat(exception.getStatusCode()).isEqualTo(401);
		assertThat(exception.getErrorCode()).isEqualTo("invalid_api_key");
		assertThat(exception.getMessage()).isEqualTo("Invalid authentication credentials");
	}

	@Test
	void mapsANonJsonErrorBodyToAFallbackMessage() {
		wireMockServer.stubFor(get(urlPathEqualTo("/v1/latest"))
				.willReturn(aResponse().withStatus(500).withBody("<html>Server error</html>")));

		final var exception = catchThrowableOfType(() -> provider.getExchangeRates(USD, List.of(EUR)),
				CurrencyExchangeApiException.class);

		assertThat(exception.getStatusCode()).isEqualTo(500);
		assertThat(exception.getErrorCode()).isNull();
		assertThat(exception.getMessage()).isEqualTo("The currency exchange API returned HTTP 500.");
	}

	@Test
	void mapsAnErrorBodyWithOnlyAMessageField() {
		wireMockServer.stubFor(get(urlPathEqualTo("/v1/latest")).willReturn(aResponse().withStatus(404)
				.withBody("{\"message\": \"A requested endpoint does not exist\"}")));

		final var exception = catchThrowableOfType(() -> provider.getExchangeRates(USD, List.of(EUR)),
				CurrencyExchangeApiException.class);

		assertThat(exception.getMessage()).isEqualTo("A requested endpoint does not exist");
		assertThat(exception.getErrorCode()).isNull();
	}

	@Test
	void mapsAnErrorBodyWithOnlyAnErrorObjectAndNoTopLevelMessage() {
		wireMockServer.stubFor(get(urlPathEqualTo("/v1/latest")).willReturn(aResponse().withStatus(403)
				.withBody("{\"error\": {\"code\": \"forbidden\", \"message\": \"nope\"}}")));

		final var exception = catchThrowableOfType(() -> provider.getExchangeRates(USD, List.of(EUR)),
				CurrencyExchangeApiException.class);

		assertThat(exception.getMessage()).isEqualTo("The currency exchange API returned HTTP 403.");
		assertThat(exception.getErrorCode()).isEqualTo("forbidden");
	}

	@Test
	void treatsAMalformedSuccessBodyAsAnApiResponseError() {
		wireMockServer.stubFor(get(urlPathEqualTo("/v1/latest")).willReturn(aResponse().withStatus(200).withBody("not-json")));

		final var exception = catchThrowableOfType(() -> provider.getExchangeRates(USD, List.of(EUR)),
				CurrencyExchangeApiException.class);

		assertThat(exception.getErrorCode()).isEqualTo("invalid_json");
	}

	@Test
	void treatsAnEmptySuccessBodyAsAnApiResponseError() {
		wireMockServer.stubFor(get(urlPathEqualTo("/v1/latest")).willReturn(aResponse().withStatus(200).withBody("null")));

		final var exception = catchThrowableOfType(() -> provider.getExchangeRates(USD, List.of(EUR)),
				CurrencyExchangeApiException.class);

		assertThat(exception.getErrorCode()).isEqualTo("empty_response");
	}
}
