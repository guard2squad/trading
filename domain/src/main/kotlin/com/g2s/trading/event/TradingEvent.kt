package com.g2s.trading.event

import com.g2s.trading.indicator.CandleStick
import com.g2s.trading.indicator.MarkPrice

sealed class TradingEvent(
    source: Any
) : Event(source) {

    data class CandleStickEvent(
        val source: CandleStick
    ) : TradingEvent(source)

    data class MarkPriceRefreshEvent(
        val source: MarkPrice
    ) : TradingEvent(source)
}