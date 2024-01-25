package com.g2s.trading.openman

import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.order.Order
import com.g2s.trading.order.OrderDetail
import com.g2s.trading.order.OrderUseCase
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.SimpleStrategy
import com.g2s.trading.strategy.SimpleStrategySpecRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@Component
class SimpleOpenMan(
    private val positionUseCase: PositionUseCase,
    private val accountUseCase: AccountUseCase,
    private val simpleStrategy: SimpleStrategy,
    private val orderUseCase: OrderUseCase,
    private val simpleStrategySpecRepository: SimpleStrategySpecRepository
) : OpenMan {

    lateinit var positionWithCloseReferenceData: Position

    override fun open() {
        val simpleStrategySpecs = simpleStrategySpecRepository.findAll()
        simpleStrategySpecs.forEach { simpleStrategySpec ->
            val unUsedSymbol = simpleStrategySpec.symbols.filter { !positionUseCase.checkPositionBySymbol(it) }
            if (unUsedSymbol.isEmpty()) {
                return
            }
            val allocatedBalance = accountUseCase.getAllocatedBalancePerStrategy(
                simpleStrategySpec.asset,
                simpleStrategySpec.allocatedRatio
            )
            val availableBalance = accountUseCase.getAvailableBalance(simpleStrategySpec.asset)
            if (allocatedBalance > availableBalance) {
                return
            }
            simpleStrategy.setSpec(simpleStrategySpec)
            val orderDetail = simpleStrategy.invoke() ?: return
            when (orderDetail) {
                is OrderDetail.SimpleOrderDetail -> {
                    val order = Order(
                        symbol = orderDetail.symbol,
                        orderSide = orderDetail.orderSide,
                        orderType = orderDetail.orderType,
                        quantity = allocatedBalance.divide(BigDecimal(orderDetail.currentPrice), RoundingMode.HALF_UP)
                            .setScale(orderDetail.symbol.precision, RoundingMode.HALF_UP).toString(),
                        timestamp = LocalDateTime.now().toString()
                    )
                    positionWithCloseReferenceData =
                        orderUseCase.openOrder(order, simpleStrategySpec.simpleCloseReferenceData)
                }
            }
            positionUseCase.registerPosition(positionWithCloseReferenceData)
        }
    }
}
