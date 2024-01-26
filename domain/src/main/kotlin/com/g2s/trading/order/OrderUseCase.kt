package com.g2s.trading.order

import com.g2s.trading.Exchange
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionSide
import com.g2s.trading.position.PositionUseCase
import org.springframework.stereotype.Service

@Service
class OrderUseCase(
    private val exchangeImpl: Exchange
) {
    fun createOrder(price: Double, balance: Double, symbol: Symbol, orderSide: OrderSide): Order {

    }
}
