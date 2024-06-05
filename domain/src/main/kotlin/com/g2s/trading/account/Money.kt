package com.g2s.trading.account

import java.math.BigDecimal

sealed class Money {

    data class AvailableMoney(
        val positionAmount: BigDecimal,
        val fee: BigDecimal,
    ) : Money()

    data class NotAvailableMoney(
        val reason: String
    ) : Money()
}