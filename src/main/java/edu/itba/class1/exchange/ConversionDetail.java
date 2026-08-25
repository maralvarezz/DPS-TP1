package edu.itba.class1.exchange;

import java.math.BigDecimal;

/**
 * The result of converting an amount into a single target currency, together with the
 * exchange rate that was used, so it can be verified and compared (user story 7: "quiero
 * poder ver la cotizacion usada para cada moneda").
 *
 * @param rate            units of the target currency per unit of the base currency
 * @param convertedAmount the base amount converted at {@code rate}
 */
public record ConversionDetail(BigDecimal rate, MoneyAmount convertedAmount) {
}
