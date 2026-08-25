package edu.itba.class1.exchange;

/**
 * The exchange rate provider could not be reached at all: no HTTP response was obtained
 * (network failure, timeout, DNS error, etc). Distinguishing this from
 * {@link CurrencyExchangeApiException} lets callers tell "the API rejected us" apart from
 * "we never got to talk to the API".
 */
public class CurrencyExchangeConnectionException extends CurrencyExchangeException {

	public CurrencyExchangeConnectionException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
