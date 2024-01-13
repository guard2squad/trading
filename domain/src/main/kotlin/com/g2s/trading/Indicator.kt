package com.g2s.trading

data class Indicator(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val latestPrice: Double,
)