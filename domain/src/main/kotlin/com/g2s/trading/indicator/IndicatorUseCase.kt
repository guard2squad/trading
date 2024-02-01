package com.g2s.trading.indicator

import com.g2s.trading.exchange.Exchange
import com.g2s.trading.order.Symbol
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class IndicatorUseCase(
    private val exchangeImpl: Exchange
) {

    fun getCandleStick(symbol: Symbol, interval: Interval, limit: Int): List<CandleStick> {
        return exchangeImpl.getCandleStick(symbol, interval, limit)
    }

    fun getLastPrice(symbol: Symbol): BigDecimal {
        return BigDecimal(exchangeImpl.getLastPrice(symbol))
    }
}
