package com.g2s.trading.account

sealed class Money {

    data class AvailableMoney(
        val amount: Double
    ) : Money()

    object NotAvailableMoney : Money()
}