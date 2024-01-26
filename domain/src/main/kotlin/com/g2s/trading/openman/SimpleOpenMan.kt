package com.g2s.trading.openman

import com.g2s.trading.order.Symbol
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.indicator.IndicatorUseCase
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.Order
import com.g2s.trading.order.OrderUseCase
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.StrategySpec
import org.springframework.stereotype.Component

@Component
class SimpleOpenMan(
    private val positionUseCase: PositionUseCase,
    private val accountUseCase: AccountUseCase,
    private val orderUseCase: OrderUseCase,
    private val indicatorUseCase: IndicatorUseCase
) : OpenMan {
    override fun open(strategySpec: StrategySpec) {
        // account 정보 동기화
        accountUseCase.syncAccount()

        // 이미 포지션 있는지 확인
        if (positionUseCase.hasPosition(strategySpec.strategyKey)) return

        // spec에 운영된 symbol중에서 현재 포지션이 없는 symbol 확인
        val unUsedSymbols = strategySpec.symbols - positionUseCase.getAllUsedSymbols()
        if (unUsedSymbols.isEmpty()) return

        // 계좌 잔고 확인
        val allocatedBalance =
            accountUseCase.getAllocatedBalancePerStrategy(strategySpec.asset, strategySpec.allocatedRatio)
        val availableBalance = accountUseCase.getAvailableBalance(strategySpec.asset)
        if (allocatedBalance < availableBalance) return

        // 오픈 가능한 symbol 확인
        val hammerRatio = strategySpec.op["hammerRatio"].asDouble()
        val openableSymbol = unUsedSymbols.map { symbol ->
            val candleSticks = indicatorUseCase.getCandleStick(symbol, Interval.ONE_MINUTE, 1)
            val lastCandleStick = candleSticks.last()

            canOpen(symbol, lastCandleStick, hammerRatio)
        } ?: return

        // 오픈
        val order = Order(
            symbol = openableSymbol,
            orderSide =,
        )
        val order = orderUseCase.createOrder(order)
        val position = orderUseCase.openOrder(order)

        // TODO: position set close 조건

        // 포지션 로컬 저장소 등록
        positionUseCase.registerPosition(position)
    }

    private fun canOpen(symbol: Symbol, candleStick: CandleStick, hammerRatio: Double): OpenableSymbol {
        return
    }
}
