package com.g2s.trading.event

import com.g2s.trading.indicator.CandleStick
import com.g2s.trading.indicator.MarkPrice

sealed class NewTradingEvent(
    source: Any
) : NewEvent(source) {

    data class CandleStickEvent(
        val source: CandleStick
    ) : NewTradingEvent(source)

    data class MarkPriceRefreshEvent(
        val source: MarkPrice
    ) : NewTradingEvent(source)
}