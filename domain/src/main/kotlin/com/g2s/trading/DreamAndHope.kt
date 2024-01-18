package com.g2s.trading

import org.springframework.stereotype.Service

@Service
class DreamAndHope(
    private val exchangeImpl: Exchange,
    private val strategy: Strategy
) {
    companion object {
        private val SYMBOL = Symbols.BTCUSDT
        private const val AVAILABLE_RATIO = 0.25 // TODO(이 부분은 전략의 멤버 변수로..?)
    }

    fun test() {
        val position = exchangeImpl.getPosition(SYMBOL, System.currentTimeMillis().toString())
        if (position != null) {
            if (strategy.shouldClose(position)) {
                exchangeImpl.closePosition(position)
            }
        }

        val indicator = exchangeImpl.getIndicator(SYMBOL, "1m", 1)
        val account = exchangeImpl.getAccount(Assets.USDT, System.currentTimeMillis().toString())

        if (strategy.hasAvailableBalance(account)) {
            if (strategy.shouldOpen(indicator)) {
                exchangeImpl.setPositionMode(PositionMode.ONE_WAY_MODE)
                val order = strategy.makeOrder(
                    symbol = SYMBOL,
                    orderType = OrderType.MARKET,
                    orderSide = strategy.orderSide(indicator),
                    quantity = account.availableBalance / indicator.latestPrice * AVAILABLE_RATIO
                )
                exchangeImpl.openPosition(order)
            }
        }
    }

}
