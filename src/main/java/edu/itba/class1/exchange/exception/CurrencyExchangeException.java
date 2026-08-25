package edu.itba.class1.exchange.exception;

import edu.itba.class1.exchange.model.MoneyAmount;

/**
 * Base type for every error the currency exchange business logic can raise. Callers are
 * expected to catch this (or a subtype) and react explicitly, instead of the exchange rate
 * lookup silently failing and returning a meaningless value like {@link MoneyAmount#ZERO}
 * (user story 4: "quiero que el sistema maneje y notifique errores... en lugar de simplemente
 * fallar").
 */
public abstract class CurrencyExchangeException extends RuntimeException {

	protected CurrencyExchangeException(final String message) {
		super(message);
	}

	protected CurrencyExchangeException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
