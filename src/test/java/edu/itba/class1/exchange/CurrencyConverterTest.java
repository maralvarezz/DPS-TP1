package edu.itba.class1.exchange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.itba.class1.exchange.exception.CurrencyExchangeApiException;
import edu.itba.class1.exchange.exception.CurrencyExchangeConnectionException;
import edu.itba.class1.exchange.exception.UnknownCurrencyException;
import edu.itba.class1.exchange.model.ConversionResult;
import edu.itba.class1.exchange.model.ExchangeRate;
import edu.itba.class1.exchange.model.MoneyAmount;
import edu.itba.class1.exchange.provider.ExchangeRateProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyConverterTest {

	private static final Currency EUR = Currency.getInstance("EUR");
	private static final Currency USD = Currency.getInstance("USD");
	private static final Currency JPY = Currency.getInstance("JPY");
	private static final Currency GBP = Currency.getInstance("GBP");

	@Mock
	private ExchangeRateProvider exchangeRateProvider;

	@Test
	void constructorRejectsNullProvider() {
		assertThatThrownBy(() -> new CurrencyConverter(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void legacyConvertPreservesTheBehaviourReviewedInClass() {
		// Same scenario as the original class-1 CurrencyConverterTest: 100 EUR -> USD at 1.1528.
		when(exchangeRateProvider.getExchangeRates(EUR, List.of(USD)))
				.thenReturn(Map.of(USD, new BigDecimal("1.1528")));

		final var converter = new CurrencyConverter(exchangeRateProvider);
		final var result = converter.convert(EUR, USD, new MoneyAmount(100));

		assertThat(result).isEqualTo(new MoneyAmount(115.28));
	}

	@Test
	void legacyConvertNoLongerSwallowsProviderFailures() {
		// User story 4: a failure must be surfaced, not turned into a silent MoneyAmount.ZERO.
		final var apiException = new CurrencyExchangeApiException(500, "provider_error", "boom");
		when(exchangeRateProvider.getExchangeRates(EUR, List.of(USD))).thenThrow(apiException);

		final var converter = new CurrencyConverter(exchangeRateProvider);

		assertThatThrownBy(() -> converter.convert(EUR, USD, new MoneyAmount(100))).isSameAs(apiException);
		assertThat(apiException.getStatusCode()).isEqualTo(500);
		assertThat(apiException.getErrorCode()).isEqualTo("provider_error");
	}

	@Test
	void legacyConvertNoLongerSwallowsProviderConnectionFailures() {
		final var cause = new RuntimeException("timeout");
		final var connectionException = new CurrencyExchangeConnectionException("connection failed", cause);
		when(exchangeRateProvider.getExchangeRates(EUR, List.of(USD))).thenThrow(connectionException);

		final var converter = new CurrencyConverter(exchangeRateProvider);

		assertThatThrownBy(() -> converter.convert(EUR, USD, new MoneyAmount(100)))
				.isSameAs(connectionException)
				.hasCause(cause);
	}

	@Test
	void legacyConvertRejectsNullToCurrency() {
		final var converter = new CurrencyConverter(exchangeRateProvider);

		assertThatThrownBy(() -> converter.convert(EUR, null, new MoneyAmount(100)))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void listSupportedCurrenciesDelegatesToProvider() {
		final List<Currency> currencies = List.of(USD, EUR);
		when(exchangeRateProvider.listSupportedCurrencies()).thenReturn(currencies);

		final var converter = new CurrencyConverter(exchangeRateProvider);

		assertThat(converter.listSupportedCurrencies()).isEqualTo(currencies);
	}

	@Test
	void getExchangeRateReturnsTheRateAndAFreshTimestampWithoutConvertingAnAmount() {
		when(exchangeRateProvider.getExchangeRates(USD, List.of(EUR))).thenReturn(Map.of(EUR, new BigDecimal("0.92")));

		final var converter = new CurrencyConverter(exchangeRateProvider);
		final ExchangeRate quote = converter.getExchangeRate(USD, EUR);

		assertThat(quote.fromCurrency()).isEqualTo(USD);
		assertThat(quote.toCurrency()).isEqualTo(EUR);
		assertThat(quote.rate()).isEqualByComparingTo("0.92");
		assertThat(quote.fetchedAt()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
	}

	@Test
	void getExchangeRateThrowsWhenProviderDoesNotReturnTheRequestedCurrency() {
		when(exchangeRateProvider.getExchangeRates(USD, List.of(GBP))).thenReturn(Map.of());

		final var converter = new CurrencyConverter(exchangeRateProvider);

		final var exception = catchThrowableOfType(() -> converter.getExchangeRate(USD, GBP), UnknownCurrencyException.class);
		assertThat(exception).isNotNull();
		assertThat(exception.getCurrency()).isEqualTo(GBP);
	}

	@Test
	void convertsOneAmountToMultipleCurrenciesAtOnceUsingLatestRates() {
		when(exchangeRateProvider.getExchangeRates(USD, List.of(EUR, JPY)))
				.thenReturn(Map.of(EUR, new BigDecimal("0.9"), JPY, new BigDecimal("150")));

		final var converter = new CurrencyConverter(exchangeRateProvider);
		final ConversionResult result = converter.convert(USD, new MoneyAmount(100), List.of(EUR, JPY));

		assertThat(result.fromCurrency()).isEqualTo(USD);
		assertThat(result.amount()).isEqualTo(new MoneyAmount(100));
		assertThat(result.fetchedAt()).isNotNull();
		assertThat(result.isHistorical()).isFalse();
		assertThat(result.rateDate()).isNull();
		assertThat(result.conversions().get(EUR).rate()).isEqualByComparingTo("0.9");
		assertThat(result.conversions().get(EUR).convertedAmount()).isEqualTo(new MoneyAmount(90));
		assertThat(result.conversions().get(JPY).rate()).isEqualByComparingTo("150");
		assertThat(result.conversions().get(JPY).convertedAmount()).isEqualTo(new MoneyAmount(15000));
	}

	@Test
	void convertRejectsAnEmptyTargetCurrencyList() {
		final var converter = new CurrencyConverter(exchangeRateProvider);

		assertThatThrownBy(() -> converter.convert(USD, new MoneyAmount(100), List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void getExchangeRateRejectsANonPositiveProviderRate() {
		when(exchangeRateProvider.getExchangeRates(USD, List.of(EUR))).thenReturn(Map.of(EUR, BigDecimal.ZERO));
		final var converter = new CurrencyConverter(exchangeRateProvider);

		assertThatThrownBy(() -> converter.getExchangeRate(USD, EUR))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void convertThrowsWhenOneOfTheRequestedCurrenciesIsMissingFromTheRates() {
		when(exchangeRateProvider.getExchangeRates(USD, List.of(EUR, GBP)))
				.thenReturn(Map.of(EUR, new BigDecimal("0.9")));

		final var converter = new CurrencyConverter(exchangeRateProvider);

		assertThatThrownBy(() -> converter.convert(USD, new MoneyAmount(100), List.of(EUR, GBP)))
				.isInstanceOf(UnknownCurrencyException.class);
	}

	@Test
	void convertsUsingHistoricalRatesForAPastDate() {
		final LocalDate date = LocalDate.of(2024, 11, 20);
		when(exchangeRateProvider.getHistoricalExchangeRates(eq(USD), eq(List.of(EUR)), eq(date)))
				.thenReturn(Map.of(EUR, new BigDecimal("0.88")));

		final var converter = new CurrencyConverter(exchangeRateProvider);
		final ConversionResult result = converter.convert(USD, new MoneyAmount(100), List.of(EUR), date);

		assertThat(result.isHistorical()).isTrue();
		assertThat(result.rateDate()).isEqualTo(date);
		assertThat(result.conversions().get(EUR).convertedAmount()).isEqualTo(new MoneyAmount(88));
		verify(exchangeRateProvider).getHistoricalExchangeRates(USD, List.of(EUR), date);
		verify(exchangeRateProvider, never()).getExchangeRates(any(), any());
	}

	@Test
	void convertWithHistoricalDateRejectsNullDate() {
		final var converter = new CurrencyConverter(exchangeRateProvider);

		assertThatThrownBy(() -> converter.convert(USD, new MoneyAmount(100), List.of(EUR), null))
				.isInstanceOf(NullPointerException.class);
	}
}
