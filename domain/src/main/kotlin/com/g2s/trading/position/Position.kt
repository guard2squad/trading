package com.g2s.trading.position

import com.g2s.trading.Symbol

data class Position(
    val symbol: Symbol,
    // other properties
    val liquidationData: LiquidationData
)
