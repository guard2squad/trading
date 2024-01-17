package com.g2s.trading

import org.springframework.stereotype.Service

@Service
class DreamAndHope(
    private val exchangeImpl: Exchange,
    private val strategy: Strategy
) {
    companion object {
        private const val SYMBOL = "BTCUSDT"
        private const val AVAILABLE_RATIO = 0.25
    }

    fun test() {
        val position = exchangeImpl.getPosition("BTCUSDT", System.currentTimeMillis().toString())
        if (position != null) {
            if (strategy.shouldClose(position)) {
                exchangeImpl.closePosition(position)
            }
        }

        val indicator = exchangeImpl.getIndicator("BTCUSDT", "1m", 1)
        val account = exchangeImpl.getAccount("USDT", System.currentTimeMillis().toString())

        if (strategy.hasAvailableBalance(account)) {
            if (strategy.shouldOpen(indicator)) {
                // TODO(전략에 따라 Order 방식이 달라질건데..)
                val order = Order(
                    symbol = SYMBOL,
                    orderSide = strategy.orderSide(indicator),
                    quantity = String.format(
                        "%.3f",
                        account.availableBalance / indicator.latestPrice * AVAILABLE_RATIO
                    ),
                )
                exchangeImpl.openPosition(order)
            }
        }

    }

}