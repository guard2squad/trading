package com.g2s.trading.strategy

import com.g2s.trading.Symbol
import com.g2s.trading.indicator.IndicatorUseCase
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.OrderDetail
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.CloseReferenceData
import org.springframework.stereotype.Component

@Component
class SimpleStrategy(
    private val indicatorUseCase: IndicatorUseCase
) : Strategy<StrategySpec.SimpleStrategySpec> {

    lateinit var strategySpec: StrategySpec.SimpleStrategySpec

    override fun invoke(): StrategyResult? {

        val symbol = strategySpec.symbols.find { symbol -> canOpen(symbol) }
        if (symbol == null) {
            return null
        }

        val simpleOrderDetail = OrderDetail.SimpleOrderDetail(
            symbol = symbol,
            orderSide = getOrderSide(),
            orderType = getOrderType()
        )
        val simpleCloseReferenceData = CloseReferenceData.SimpleCloseReferenceData(
            price = indicatorUseCase.getLastPrice(symbol) // TODO(주문 후 포지션 조회할 때 갱신 필요)
        )
        val strategyResult = StrategyResult(
            orderDetail = simpleOrderDetail,
            closeReferenceData = simpleCloseReferenceData
        )
        return strategyResult
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
