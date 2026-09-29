package edu.itba.class1.exchange.domain.model;


import java.math.BigDecimal;

import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class MoneyAmountTest {

	@Test
	void exposesItsUnderlyingAmount() {
		final MoneyAmount amount = new MoneyAmount(new BigDecimal("100.00"));

		assertThat(amount.amount()).isEqualByComparingTo("100");
	}

	@Test
	void stripsTrailingZerosSoEquivalentAmountsCompareEqual() {
		assertThat(new MoneyAmount(new BigDecimal("115.2800"))).isEqualTo(new MoneyAmount(115.28));
	}

	@Test
	void rejectsANullAmount() {
		assertThatThrownBy(() -> new MoneyAmount((BigDecimal) null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void multiplyScalesTheAmountByAFactor() {
		final MoneyAmount amount = new MoneyAmount(100);

		assertThat(amount.multiply(new BigDecimal("1.5"))).isEqualTo(new MoneyAmount(150));
	}

	@Test
	void multiplyRejectsANullFactor() {
		final MoneyAmount amount = new MoneyAmount(100);

		assertThatThrownBy(() -> amount.multiply(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void zeroIsActuallyZero() {
		assertThat(MoneyAmount.ZERO.amount()).isEqualByComparingTo(BigDecimal.ZERO);
	}
}
