package edu.itba.class1.exchange.domain.exception;

public abstract class CurrencyExchangeException extends RuntimeException {

	protected CurrencyExchangeException(final String message) {
		super(message);
	}

	protected CurrencyExchangeException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
