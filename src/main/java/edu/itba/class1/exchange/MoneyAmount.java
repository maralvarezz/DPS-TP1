package edu.itba.class1.exchange;

import java.math.BigDecimal;
import java.util.Objects;

public record MoneyAmount(BigDecimal amount) {
	public static final MoneyAmount ZERO = new MoneyAmount(BigDecimal.ZERO);

	public MoneyAmount {
		amount = Objects.requireNonNull(amount, "amount must not be null").stripTrailingZeros();
	}

	public MoneyAmount(final double amount) {
		this(BigDecimal.valueOf(amount));
	}

	public MoneyAmount multiply(final BigDecimal factor) {
		return new MoneyAmount(amount.multiply(Objects.requireNonNull(factor, "factor must not be null")));
	}
}
