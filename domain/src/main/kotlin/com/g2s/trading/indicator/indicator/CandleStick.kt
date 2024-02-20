package com.g2s.trading.indicator.indicator

import com.g2s.trading.order.Symbol

data class CandleStick(
    val symbol: Symbol,
    val interval: Interval,
    val key: Long, // unique
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val numberOfTrades: Int,    // 거래 횟수
)
