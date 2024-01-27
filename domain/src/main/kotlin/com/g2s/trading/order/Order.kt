package com.g2s.trading.order

data class Order (
    val strategyKey: String,
    val symbol: Symbol,
    val orderSide: OrderSide,
    val orderType: OrderType,
    val quantity: Double
)
