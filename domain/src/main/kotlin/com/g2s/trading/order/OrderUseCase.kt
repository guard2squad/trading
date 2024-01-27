package com.g2s.trading.order

import com.g2s.trading.Exchange
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionUseCase
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class OrderUseCase(
    private val positionUseCase: PositionUseCase
) {
    fun createOrder(
        strategyKey: String,
        price: BigDecimal,
        balance: BigDecimal,
        symbol: Symbol,
        orderSide: OrderSide,
        orderType: OrderType
    ): Order {
        val quantity = balance.divide(price, symbol.precision, RoundingMode.DOWN).toDouble()
        return Order(strategyKey, symbol, orderSide, orderType, quantity)
    }

    fun openOrder(order: Order): Position {
        return positionUseCase.openPosition(order)
    }
}
