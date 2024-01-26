package com.g2s.trading.order

data class Order (
    val symbol: Symbol,
    val quantity: Double,
    val orderSide: OrderSide
)
