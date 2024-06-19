package com.g2s.trading.indicator

import com.g2s.trading.symbol.Symbol
import java.util.concurrent.ConcurrentHashMap

object LastCandles {
    private val candles: MutableMap<Symbol, CandleStick> = ConcurrentHashMap()

    fun get(symbol: Symbol): CandleStick? {
        return candles[symbol]
    }

    fun update(candleStick: CandleStick): CandleStickUpdateResult {
        lateinit var result: CandleStickUpdateResult

        candles.compute(candleStick.symbol) { _, old ->
            old?.let {
                result = CandleStickUpdateResult.Success(old, candleStick)
            } ?: run {
                result = CandleStickUpdateResult.Failed
            }

            candleStick
        }

        return result
    }
}