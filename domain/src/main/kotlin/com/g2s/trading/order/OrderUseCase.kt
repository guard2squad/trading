package com.g2s.trading.order

import com.g2s.trading.Exchange
import com.g2s.trading.position.Position
import org.springframework.stereotype.Service

@Service
class OrderUseCase(
    private val exchangeImpl: Exchange
) {

    fun createOrder(orderDetail: OrderDetail) : Position {
        // order -> position
    }
}
