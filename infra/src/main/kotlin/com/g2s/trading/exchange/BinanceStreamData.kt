package com.g2s.trading.exchange

import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.Symbol


sealed class BinanceStreamData {
    data class CandleStickData(val symbol: Symbol, val interval: Interval, val candleStick: CandleStick) :
        BinanceStreamData()

    data class MarkPriceData(val symbol: Symbol, val markPrice: Double) : BinanceStreamData()
}
