package com.g2s.trading.openman

import com.g2s.trading.Symbol
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.account.Asset
import com.g2s.trading.order.*
import com.g2s.trading.position.CloseReferenceData
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.SimpleStrategy
import com.g2s.trading.strategy.StrategySpec
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class SimpleOpenMan(
    private val positionUseCase: PositionUseCase,
    private val accountUseCase: AccountUseCase,
    private val simpleStrategy: SimpleStrategy,
    private val orderUseCase: OrderUseCase
) : OpenMan {

    lateinit var position: Position

    val simpleStrategySpec = StrategySpec.SimpleStrategySpec(
        symbols = listOf<Symbol>(Symbol.BTCUSDT),
        strategyKey = "simple",
        asset = Asset.USDT,
        hammerRatio = 2.0,
        allocatedRatio = 0.25
    )

    override fun open() {
        val unUsedSymbol = simpleStrategySpec.symbols.filter { !positionUseCase.checkPositionBySymbol(it) }
        if (unUsedSymbol.isEmpty()) {
            return
        }
        val allocatedBalance = accountUseCase.getAllocatedBalancePerStrategy(
            simpleStrategySpec.asset,
            simpleStrategySpec.allocatedRatio
        )
        val availableBalance = accountUseCase.getAvailableBalance(simpleStrategySpec.asset)
        if (allocatedBalance < availableBalance) {
            return
        }
        simpleStrategy.setSpec(simpleStrategySpec)
        val strategyResult = simpleStrategy.invoke() ?: return
        when (strategyResult.orderDetail) {
            is OrderDetail.SimpleOrderDetail -> {
                val order = Order(
                    symbol = strategyResult.orderDetail.symbol,
                    orderSide = strategyResult.orderDetail.orderSide,
                    orderType = strategyResult.orderDetail.orderType,
                    quantity = allocatedBalance.toString(),
                    timestamp = LocalDateTime.now().toString()
                )
                position = orderUseCase.openOrder(order)
            }
        }
        when (strategyResult.closeReferenceData) {
            is CloseReferenceData.SimpleCloseReferenceData -> {
                position = positionUseCase.addCloseReferenceData(position, strategyResult.closeReferenceData)
                positionUseCase.registerPosition(position)
            }
        }
    }
}
