package com.g2s.trading.order

import com.g2s.trading.order.OrderSide
import com.g2s.trading.symbol.Symbol

data class OrderResult(
    val orderId: String,
    val symbol: Symbol,
    val price: Double,
    val amount: Double,
    val side: OrderSide,
)
