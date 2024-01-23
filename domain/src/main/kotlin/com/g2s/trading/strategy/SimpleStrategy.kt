package com.g2s.trading.strategy

import com.g2s.trading.indicator.IndicatorUseCase
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.OrderDetail
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.LiquidationData
import org.springframework.stereotype.Component

@Component
class SimpleStrategy(
    private val indicatorUseCase: IndicatorUseCase
) : Strategy<StrategySpec.SimpleStrategySpec> {

    lateinit var strategySpec: StrategySpec.SimpleStrategySpec

    override fun invoke(): StrategyResult? {

        val symbol = strategySpec.symbols.find { symbol ->
            // 포지션 Open Condition
            val candleSticks = indicatorUseCase.getCandleStick(symbol, Interval.ONE_MINUTE, 1)
            val lastCandleStick = candleSticks.last()
            (lastCandleStick.high - lastCandleStick.low) / (lastCandleStick.close - lastCandleStick.open) > strategySpec.hammerRatio
        }
        if (symbol == null) {
            return null
        }

        val simpleOrderDetail = OrderDetail.SimpleOrderDetail(
            orderSide = getOrderSide(),
            orderType = getOrderType()
        )
        val simpleLiquidationData = LiquidationData.SimpleLiquidationData(
            price = indicatorUseCase.getLastPrice(symbol) // 현재 가격
        )
        val strategyResult = StrategyResult(
            orderDetail = simpleOrderDetail,
            liquidationData = simpleLiquidationData
        )
        return strategyResult
    }

    override fun setSpec(strategySpec: StrategySpec.SimpleStrategySpec) {
        this.strategySpec = strategySpec
    }

    fun getOrderSide() : OrderSide {
        return OrderSide.BUY
    }

    fun getOrderType() : OrderType {
        return OrderType.MARKET
    }

}
