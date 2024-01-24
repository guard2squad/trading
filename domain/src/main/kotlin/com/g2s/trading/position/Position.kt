package com.g2s.trading.position

import com.g2s.trading.Symbol

data class Position(
    val symbol: Symbol,
    val entryPrice: Double,
    val positionAmt: Double,
    val closeReferenceData: CloseReferenceData
)
