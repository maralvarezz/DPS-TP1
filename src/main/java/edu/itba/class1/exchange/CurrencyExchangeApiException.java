package edu.itba.class1.exchange;

/**
 * The exchange rate provider answered, but with an error (e.g. HTTP 404, 401, 429 or 500),
 * as opposed to a connection failure. Carries the HTTP status code and, when the provider
 * supplied one, its own machine-readable error code, so the caller can react accordingly
 * instead of just seeing "something went wrong".
 */
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

	/**
	 * The provider's own machine-readable error code (e.g. "invalid_api_key"), or {@code null}
	 * when the error response did not include one or could not be parsed.
	 */
	public String getErrorCode() {
		return errorCode;
	}
}
