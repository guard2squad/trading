package com.g2s.trading.order

import com.g2s.trading.Symbol
import java.time.LocalDateTime

data class Order (
    val symbol: Symbol,
    val orderSide: OrderSide,
    val orderType: OrderType,
    val quantity: String,
    val timestamp: String = LocalDateTime.now().toString()
)

