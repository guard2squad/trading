package com.g2s.trading.indicator

import com.g2s.trading.symbol.Symbol
import java.util.*

data class CandleStick(
    val key: String = UUID.randomUUID().toString(),
    val symbol: Symbol,
    val interval: Interval,
    val openTime: Long,  // 캔들스틱 시작 시간
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val numberOfTrades: Int,    // 거래 횟수
)
