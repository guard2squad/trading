package com.g2s.trading

import org.springframework.stereotype.Service

@Service
class DreamAndHope(
    private val exchangeImpl: Exchange
) {
    companion object {
        private const val UNREALIZED_PROFIT = 5
        private const val UNREALIZED_LOSS = -2
        private const val HAMMER_RATIO = 1.0
        private const val AVAILABLE_STRATEGY_RATIO = 0.25
        private const val SYMBOL = "BTCUSDT"
    }

    fun test(positionMode: PositionMode) {
        val position = exchangeImpl.getPosition("BTCUSDT", System.currentTimeMillis().toString())
        if (position != null) {
            if (shouldClose(position)) {
                exchangeImpl.closePosition(
                    position,
                    positionMode = positionMode,
                    positionSide = PositionSide.BOTH
                )
            //TODO(PositonMode, PostionSide는 Position이라는
            // 단어가 붙어있지만 포지션과 다른 개념이다. 바이낸스 API에 의존함
            // 따라서 포지션 모드는 이 전략에서 전역적으로 유지되는 상태인데)
            }
        }

        val indicator = exchangeImpl.getIndicator("BTCUSDT", "1m", 1)
        val account = exchangeImpl.getAccount("USDT", System.currentTimeMillis().toString())

        if (hasAvailableBalance(account, AVAILABLE_STRATEGY_RATIO)) {
            if (shouldOpen(indicator, HAMMER_RATIO)) {
                val order = Order(
                    symbol = SYMBOL,
                    orderSide = orderSide(indicator),
                    quantity = String.format(
                        "%.3f",
                        account.availableBalance / indicator.latestPrice * AVAILABLE_STRATEGY_RATIO
                    ),
                )
                exchangeImpl.openPosition(
                    order,
                    positionMode = positionMode,
                    positionSide = PositionSide.BOTH
                )
            }
        }

    }

    private fun shouldClose(position: Position): Boolean {
        return position.unRealizedProfit > UNREALIZED_PROFIT || position.unRealizedProfit < UNREALIZED_LOSS
    }

    private fun hasAvailableBalance(account: Account, ratio: Double): Boolean {
        return account.availableBalance > account.balance * ratio
    }

    private fun orderSide(indicator: Indicator): OrderSide {
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