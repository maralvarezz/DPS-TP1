package edu.itba.class1.exchange;

import java.util.Currency;

/**
 * The provider's response did not include a rate for a currency that was requested
 * (e.g. an unsupported currency code).
 */
public class UnknownCurrencyException extends CurrencyExchangeException {

	private final Currency currency;

	public UnknownCurrencyException(final Currency currency) {
		super("No exchange rate was returned for currency '" + currency.getCurrencyCode() + "'.");
		this.currency = currency;
	}

	public Currency getCurrency() {
		return currency;
	}
}
