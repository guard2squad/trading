package com.g2s.trading.openman

import com.g2s.trading.Symbol
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.account.Asset
import com.g2s.trading.order.OrderUseCase
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.SimpleStrategy
import com.g2s.trading.strategy.StrategySpec
import org.springframework.stereotype.Component

@Component
class SimpleOpenMan(
    private val positionUseCase: PositionUseCase,
    private val accountUseCase: AccountUseCase,
    private val simpleStrategy: SimpleStrategy,
    private val orderUseCase: OrderUseCase
) : OpenMan {

    val simpleStrategySpec = StrategySpec.SimpleStrategySpec(
        symbols = listOf<Symbol>(Symbol.BTCUSDT),
        strategyKey = "simple",
        asset = Asset.USDT,
        hammerRatio = 0.5,
        allocatedRatio = 0.25
    )

    override fun open() {
        val unUsedSymbol = simpleStrategySpec.symbols.filter { !positionUseCase.checkPositionBySymbol(it) }
        if (unUsedSymbol.isEmpty()) {
            return
        }
        simpleStrategy.setSpec(simpleStrategySpec.copy(symbols = unUsedSymbol))

        val allocatedBalance = accountUseCase.getAllocatedBalancePerStrategy(
            simpleStrategySpec.asset,
            simpleStrategySpec.hammerRatio
        )
        val availableBalance = accountUseCase.getAvailableBalance(simpleStrategySpec.asset)
        if (allocatedBalance < availableBalance) {
            return
        }

        val strategyResult = simpleStrategy.invoke() ?: return
        val order = orderUseCase.createOrder(strategyResult.orderDetail)
        // TODO(sendOrder 구현)
        orderUseCase.sendOrder(order)
        val position = positionUseCase.createPosition(strategyResult.liquidationData)
        positionUseCase.registerPosition(position)
    }
}
