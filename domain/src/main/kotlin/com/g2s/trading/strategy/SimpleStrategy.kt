package com.g2s.trading.strategy

import com.g2s.trading.Symbol
import com.g2s.trading.indicator.IndicatorUseCase
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.OrderDetail
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import org.springframework.stereotype.Component

@Component
class SimpleStrategy(
    private val indicatorUseCase: IndicatorUseCase
) : Strategy<StrategySpec.SimpleStrategySpec> {

    lateinit var strategySpec: StrategySpec.SimpleStrategySpec

    override fun invoke(): OrderDetail? {

        val symbol = strategySpec.symbols.find { symbol -> canOpen(symbol) }
        if (symbol == null) {
            return null
        }

        val simpleOrderDetail = OrderDetail.SimpleOrderDetail(
            symbol = symbol,
            orderSide = getOrderSide(),
            orderType = getOrderType(),
            currentPrice = indicatorUseCase.getLastPrice(symbol)
        )
        return simpleOrderDetail
    }

    override fun setSpec(strategySpec: StrategySpec.SimpleStrategySpec) {
        this.strategySpec = strategySpec
    }

    private fun canOpen(symbol: Symbol): Boolean {
        val candleSticks = indicatorUseCase.getCandleStick(symbol, Interval.ONE_MINUTE, 1)
        val lastCandleStick = candleSticks.last()
        return (lastCandleStick.high - lastCandleStick.low) / (lastCandleStick.close - lastCandleStick.open) > strategySpec.hammerRatio
    }

    private fun getOrderSide() : OrderSide {
        return OrderSide.BUY
    }

    private fun getOrderType() : OrderType {
        return OrderType.MARKET
    }

}
