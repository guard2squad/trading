package com.g2s.trading.order

import com.g2s.trading.exchange.Exchange
import com.g2s.trading.account.NewAccountUseCase
import com.g2s.trading.position.NewPositionUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NewOrderUseCase(
    private val exchangeImpl: Exchange,
    private val positionUseCase: NewPositionUseCase,
    private val accountUseCase: NewAccountUseCase,
    private val orderRepository: OrderRepository
    // order repository
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val orders: MutableMap<String, NewOrder> = mutableMapOf()

    init {
        orderRepository.findAllPendingOrders().forEach { order ->
            orders[order.id] = order
        }
    }

    fun sendOrder(vararg order: NewOrder) {
        order.forEach { send(it) }
    }

    fun handleResult(result: OrderResult) {
    }


    private fun send(order: NewOrder) {
    }
}