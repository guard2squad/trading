package com.g2s.trading.order

import com.g2s.trading.exchange.Exchange
import com.g2s.trading.account.NewAccountUseCase
import com.g2s.trading.position.NewPositionUseCase
import org.springframework.stereotype.Service

@Service
class NewOrderUseCase(
    private val exchangeImpl: Exchange,
    private val positionUseCase: NewPositionUseCase,
    private val accountUseCase: NewAccountUseCase,
    private val orderRepository: OrderRepository
    // order repository
) {
    private val pendingOrders: MutableMap<String, NewOrder> = mutableMapOf()

    init {
        // TODO("load pending orders")
        orderRepository.findAllPendingOrders().forEach { order ->
            pendingOrders[order.id] = order
        }
    }

    fun sendOrder(vararg order: NewOrder) {
        order.forEach { send(it) }
    }

    // TODO("인프라에서 호출")
    fun handleResult(result: OrderResult) {
        when (val pendingOrder = removePendingOrder(result.orderId)) {
            // open complete
            is NewOpenOrder -> {
                syncAccount(pendingOrder, result)
                positionUseCase.openPosition(pendingOrder as NewOpenOrder.MarketOrder, result)
            }

            // close complete
            is NewCloseOrder -> {
                val removedPosition = positionUseCase.closePosition(pendingOrder.positionId)
                removedPosition?.let { position ->
                    position.closeOrders.filterNot { order ->
                        order.id == result.orderId
                    }.forEach {
                        cancelOrder(it.id)
                    }

                } ?: throw RuntimeException("position not exist: ${pendingOrder.positionId}")
            }
        }
    }

    private fun cancelOrder(orderId: String) {
        pendingOrders.remove(orderId)?.let { order ->
            exchangeImpl.cancelOrder(order.symbol, order.id)
            // TODO("order repository remove pending order")
            orderRepository.deletePendingOrder(orderId)
        }
    }

    private fun removePendingOrder(orderId: String): NewOrder {
        return pendingOrders.remove(orderId)?.let { order ->
            // TODO("order repository remove pending order")
            orderRepository.deletePendingOrder(orderId)
            order
        } ?: throw RuntimeException("pending order not exist: $orderId")
    }

    private fun send(order: NewOrder) {
        pendingOrders[order.id] = order

        if (order is NewCloseOrder) {
            positionUseCase.addCloseOrder(order)
        }

        val result = try {
            exchangeImpl.sendOrder(order)
            SendOrderResult.Success
        } catch (e: Exception) {
            if (order is NewCloseOrder) positionUseCase.removeCloseOrder(order) // undo
            pendingOrders.remove(order.id)
            SendOrderResult.Failure(e)
        }

        if (result is SendOrderResult.Success) {
            // TODO("order repository write pending order")
            orderRepository.savePendingOrder(order)
        }
    }

    private fun syncAccount(pendingOrder: NewOpenOrder, orderResult: OrderResult) {
        val expectedMoney = pendingOrder.price * pendingOrder.amount
        val realMoney = orderResult.price * orderResult.amount

        // 예상치 - 실제값을 다시 입금 (실제값이 큰 경우, -입금 됨)
        accountUseCase.deposit(expectedMoney - realMoney)
    }
}