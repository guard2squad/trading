package com.g2s.trading

import java.time.LocalDateTime

data class Order (
    val symbol: String,
    val orderSide: OrderSide,
    val quantity: String,
    val type: OrderType = OrderType.MARKET,
    val positionSide: PositionSide, // ONE_WAY_MODE
    val timestamp: String = LocalDateTime.now().toString()
)