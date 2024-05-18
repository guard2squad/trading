package com.g2s.trading.indicator

import com.g2s.trading.symbol.Symbol
import java.time.Instant
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

        candles.compute(candleStick.symbol) { _, old ->
            old?.let {
                // 이미 가지고 있는 캔들스틱인 경우
                if (old.key == candleStick.key) {
                    result = CandleStickUpdateResult.Failed
                    old
                }
                //            // 1분 차이 캔들스틱이 아닌 경우
//            else if (old.key + 60000L != candleStick.key) {
//                result = CandleStickUpdateResult.Failed
//                old
//            } // 갱신된지 1초 이상된 캔들스틱인 경우
//            else if (Instant.now().toEpochMilli() - candleStick.key > 1000) {
//                result = CandleStickUpdateResult.Failed
//                old
//            }
                // 이 로직이 전략에 있어야 할지, 여기에 있어야할지?
                else {
                    result = old.let { CandleStickUpdateResult.Success(it) }
                    candleStick
                }
            } ?: run {
                result = CandleStickUpdateResult.Failed
                candleStick
            }
        }

        return result
    }
}