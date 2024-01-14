package com.g2s.trading

import java.time.LocalDateTime

data class Order (
    val symbol: String,
    val side: Side,
    val quantity: String,
    val type: String,
    val positionSide: String = "BOTH", // ONE_WAY_MODE
    val timestamp: String = LocalDateTime.now().toString()
)