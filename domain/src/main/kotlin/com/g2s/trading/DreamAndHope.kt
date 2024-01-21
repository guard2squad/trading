package com.g2s.trading

import com.g2s.trading.strategy.Strategy
import com.g2s.trading.strategy.StrategyUseCase
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

@Service
class DreamAndHope(
    private val exchangeImpl: Exchange,
    private val strategyUseCase: StrategyUseCase
) {
    companion object {
//        private val SYMBOL = Symbols.BTCUSDT
//        private const val AVAILABLE_RATIO = 0.25
    }

    fun test() {
        val strategies = strategyUseCase.getStrategies()
        strategies.parallelStream().forEach { it.invoke() }


//
//val indicator = exchangeImpl.getIndicator(SYMBOL, "1m", 1)
//
//        val account = exchangeImpl.getAccount(Assets.USDT, System.currentTimeMillis().toString())
//
//        if (strategy.hasAvailableBalance(account)) {
//            if (strategy.shouldOpen(indicator)) {
//                exchangeImpl.setPositionMode(PositionMode.ONE_WAY_MODE)
//                val order = strategy.makeOrder(
//                    symbol = SYMBOL,
//                    orderType = OrderType.MARKET,
//                    orderSide = strategy.orderSide(indicator),
//                    quantity = account.availableBalance / indicator.latestPrice * AVAILABLE_RATIO
//                )
//                exchangeImpl.openPosition(order)
//            }
//        }
    }

}
