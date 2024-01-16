package com.g2s.trading

import java.time.LocalDateTime

data class Order (
    val symbol: String,
    val orderSide: OrderSide,
    val orderType: OrderType = OrderType.MARKET,
    val quantity: String,
    val timestamp: String = LocalDateTime.now().toString()
)

