package com.g2s.trading.indicator

import com.g2s.trading.symbol.Symbol
import java.util.concurrent.ConcurrentHashMap

object LastCandles {
    private val candles: MutableMap<Symbol, CandleStick> = ConcurrentHashMap()

    fun get(symbol: Symbol): CandleStick? {
        return candles[symbol]
    }

    /**
     * @return 업데이트 성공시 기존 데이터
     */
    fun update(candleStick: CandleStick): CandleStickUpdateResult {
        lateinit var result: CandleStickUpdateResult

        candles.computeIfPresent(candleStick.symbol) { _, old ->
            if (old.key == candleStick.key) {
                result = CandleStickUpdateResult.Failed
                old
            } else {
                result = CandleStickUpdateResult.Success(old)
                candleStick
            }
        }

        return result
    }
}