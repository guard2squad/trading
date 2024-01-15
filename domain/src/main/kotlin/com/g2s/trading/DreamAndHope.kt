package com.g2s.trading

import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class DreamAndHope(
    private val exchangeImpl: Exchange
) {
    companion object {
        private const val UNREALIZED_PROFIT = 5
        private const val UNREALIZED_LOSS = 0
        private const val HAMMER_RATIO = 1.0
        private const val AVAILABLE_STRATEGY_RATIO = 0.25
        private const val SYMBOL = "BTCUSDT"
        private const val TYPE = "MARKET"
    }

    fun test() {
        val position = exchangeImpl.getPosition("BTCUSDT", System.currentTimeMillis().toString())
        if (position != null) {
            if (shouldClose(position)) {
                exchangeImpl.closePosition(
                    position,
                    positionMode = PositionMode.ONE_WAY_MODE,
                    positionSide = PositionSide.BOTH
                )
            }
        }

        val indicator = exchangeImpl.getIndicator("BTCUSDT", "1m", 1)
        val account = exchangeImpl.getAccount("USDT", System.currentTimeMillis().toString())

        if (hasAvailableBalance(account, AVAILABLE_STRATEGY_RATIO)) {
            if (shouldOpen(indicator, HAMMER_RATIO)) {
                val order = Order(
                    symbol = SYMBOL,
                    orderSide = side(indicator),
                    quantity = String.format(
                        "%.3f",
                        account.availableBalance / indicator.latestPrice * AVAILABLE_STRATEGY_RATIO
                    ),
                    positionSide = PositionSide.BOTH, // ONE_WAY_MODE
                    timestamp = LocalDateTime.now().toString()
                )
                exchangeImpl.openPosition(order) // TODO openPosition(position), 도메인적으로 고민 좀
            }
        }

    }

    private fun shouldClose(position: Position): Boolean {
        return position.unRealizedProfit > UNREALIZED_PROFIT || position.unRealizedProfit < UNREALIZED_LOSS
    }

    private fun hasAvailableBalance(account: Account, ratio: Double): Boolean {
        return account.availableBalance > account.balance * ratio
    }

    private fun side(indicator: Indicator): OrderSide {
        return if (indicator.open < indicator.close) {
            OrderSide.BUY
        } else {
            OrderSide.SELL
        }
    }

    private fun shouldOpen(indicator: Indicator, ratio: Double): Boolean {
        return (indicator.high - indicator.low) / (indicator.close - indicator.open) > ratio
    }
}