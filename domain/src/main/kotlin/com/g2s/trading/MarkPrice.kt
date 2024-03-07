package com.g2s.trading

import com.g2s.trading.symbol.Symbol

data class MarkPrice(
    val symbol: Symbol,
    val price: Double
)
