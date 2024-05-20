package com.g2s.trading.order

import com.g2s.trading.symbol.Symbol

data class OrderResult(
    val type: OrderResultType,
    val orderId: String,
    val symbol: Symbol,
    val price: Double,
    val amount: Double,
    val commission: Double
)
