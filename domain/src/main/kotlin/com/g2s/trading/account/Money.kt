package com.g2s.trading.account

import java.math.BigDecimal

sealed class Money {

    data class AvailableMoney(
        val allocatedAmount: BigDecimal,
        val expectedFee: BigDecimal,
    ) : Money()

    object NotAvailableMoney : Money()
}