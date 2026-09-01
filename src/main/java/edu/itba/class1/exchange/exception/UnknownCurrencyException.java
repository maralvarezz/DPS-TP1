package edu.itba.class1.exchange.exception;

import java.util.Currency;


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
