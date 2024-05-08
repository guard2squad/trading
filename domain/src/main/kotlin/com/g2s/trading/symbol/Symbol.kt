package com.g2s.trading.symbol

data class Symbol(
    val value: String,
    val quantityPrecision: Int,
    val pricePrecision: Int,
    val minimumNotionalValue: Double,
    val minimumPrice: Double,
    val tickSize: Double,
    val commissionRate: Double,
)