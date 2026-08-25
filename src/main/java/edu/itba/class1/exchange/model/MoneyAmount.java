package edu.itba.class1.exchange.model;

import java.math.BigDecimal;
import java.util.Objects;

public record MoneyAmount(BigDecimal amount) {
	public static final MoneyAmount ZERO = new MoneyAmount(BigDecimal.ZERO);

	public MoneyAmount {
		amount = Objects.requireNonNull(amount, "amount must not be null").stripTrailingZeros();
		if (amount.scale() < 0) {
			// stripTrailingZeros() gives round numbers a negative scale (e.g. 100 -> "1E+2"),
			// which prints in scientific notation; clamp back to a plain, zero-scale integer.
			amount = amount.setScale(0);
		}
	}

	public MoneyAmount(final double amount) {
		this(BigDecimal.valueOf(amount));
	}

	public MoneyAmount multiply(final BigDecimal factor) {
		return new MoneyAmount(amount.multiply(Objects.requireNonNull(factor, "factor must not be null")));
	}
}
