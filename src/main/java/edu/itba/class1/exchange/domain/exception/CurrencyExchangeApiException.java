package edu.itba.class1.exchange.domain.exception;

public class CurrencyExchangeApiException extends CurrencyExchangeException {

	private final int statusCode;
	private final String errorCode;

	public CurrencyExchangeApiException(final int statusCode, final String errorCode, final String message) {
		super(message);
		this.statusCode = statusCode;
		this.errorCode = errorCode;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public String getErrorCode() {
		return errorCode;
	}
}
