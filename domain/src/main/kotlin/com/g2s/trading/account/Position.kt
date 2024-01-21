package com.g2s.trading.account

import com.g2s.trading.Symbol

data class Position(
    val symbol: Symbol,
    val entryPrice: Double,
    val positionAmt: Double
)
