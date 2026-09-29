package edu.itba.class1.exchange.domain.model;

import java.math.BigDecimal;

public record ConversionDetail(BigDecimal rate, MoneyAmount convertedAmount) {
}
