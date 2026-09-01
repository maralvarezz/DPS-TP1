package edu.itba.class1.exchange.model;

import java.math.BigDecimal;

public record ConversionDetail(BigDecimal rate, MoneyAmount convertedAmount) {
}
