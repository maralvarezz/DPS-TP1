package edu.itba.class1.exchange.domain.interfaces;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;

public interface ExchangeRateProvider {

	List<Currency> listSupportedCurrencies();

	Map<Currency, BigDecimal> getExchangeRates(Currency fromCurrency, List<Currency> toCurrencies);

	Map<Currency, BigDecimal> getHistoricalExchangeRates(Currency fromCurrency, List<Currency> toCurrencies, LocalDate date);
}
