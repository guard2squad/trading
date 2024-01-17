package com.g2s.trading

import org.springframework.stereotype.Component

@Component
class SimpleStrategy : Strategy {

    private val symbol = "BTCUSDT"
    private val orderType = OrderType.MARKET
    private val availableStrategyRatio = 0.25
    private val hammerRatio = 1.0
    private val unrealizedProfit = 5.0
    private val unrealizedLoss = -2.0

    override fun shouldOpen(indicator: Indicator): Boolean {
        return (indicator.high - indicator.low) / (indicator.close - indicator.open) > hammerRatio
    }

    override fun shouldClose(position: Position): Boolean {
        return position.unRealizedProfit > unrealizedProfit || position.unRealizedProfit < unrealizedLoss
    }

    override fun hasAvailableBalance(account: Account): Boolean {
        return account.availableBalance > account.balance * availableStrategyRatio
    }

    override fun orderSide(indicator: Indicator): OrderSide {
        return if (indicator.open < indicator.close) {
            OrderSide.BUY
        } else {
            OrderSide.SELL
        }
    }

    override fun makeOrder(symbol: String, orderType: OrderType, orderSide: OrderSide, quantity: Double): Order {
        return Order(
            symbol = symbol,
            orderType = orderType,
            orderSide = orderSide,
            quantity = String.format("%.3f", quantity),
        )
    }
}