package com.g2s.trading

import java.time.LocalDateTime

data class Order (
    val symbol: Symbols,
    val orderSide: OrderSide,
    val orderType: OrderType,
    val quantity: String,
    val timestamp: String = LocalDateTime.now().toString()
)

