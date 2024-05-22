package com.g2s.trading.order

import com.g2s.trading.exchange.Exchange
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.position.PositionUseCase
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class OrderUseCase(
    private val exchangeImpl: Exchange,
    private val positionUseCase: PositionUseCase,
    private val accountUseCase: AccountUseCase,
    private val orderRepository: OrderRepository
    // order repository
) {
    private val pendingOrders: MutableMap<String, Order> = mutableMapOf()

    fun sendOrder(vararg order: Order) {
        order.forEach { send(it) }
    }

    fun handleResult(result: OrderResult) {
        when (result) {
            is OrderResult.New -> {
                val order = pendingOrders.remove(result.orderId)
                order?.run {
                    orderRepository.deletePendingOrder(result.orderId)
                    when (order) {
                        is OpenOrder -> {
                            positionUseCase.openPosition(order)
                        }

                        is CloseOrder -> {
                            positionUseCase.addCloseOrder(order)
                        }

                        else -> {}
                    }
                } ?: RuntimeException("invalid order id ${result.orderId}")

            }

            is OrderResult.FilledOrderResult.PartiallyFilled -> {
                val positionResult = positionUseCase.findOpenedPosition(result.orderId)
                positionResult?.run {
                    val position = positionResult.first
                    val isOpen = positionResult.second
                    if (isOpen) {
                        positionUseCase.updateOpenedPosition(result)
                        val openOrder = position.openOrder
                        val expectedExpense = BigDecimal(openOrder.originalPrice) * BigDecimal(result.amount)
                        val actualExpense = BigDecimal(result.price) * BigDecimal(result.amount)
                        val expenseVariance = (expectedExpense - actualExpense).toDouble()
                        accountUseCase.deposit(expenseVariance) // 차액 입금 (음수/양수 상관없음)
                        accountUseCase.deposit(-result.commission)  // 수수료 음수로 입금
                    } else {
                        positionUseCase.updateClosePosition(result)
                        val realizedPnL = (BigDecimal(result.price) * BigDecimal(result.amount)).toDouble()
                        accountUseCase.deposit(realizedPnL) // 수익 입금
                    }
                }

            }

            is OrderResult.FilledOrderResult.Filled -> {
                val positionResult = positionUseCase.findOpenedPosition(result.orderId)
                positionResult?.run {
                    val position = positionResult.first
                    val isOpen = positionResult.second
                    if (isOpen) {
                        positionUseCase.updateOpenedPosition(result)
                        val openOrder = position.openOrder
                        val expectedExpense = BigDecimal(openOrder.originalPrice) * BigDecimal(result.amount)
                        val actualExpense = BigDecimal(result.price) * BigDecimal(result.amount)
                        val expenseVariance = (expectedExpense - actualExpense).toDouble()
                        accountUseCase.deposit(expenseVariance) // 차액 입금 (음수/양수 상관없음)
                        accountUseCase.deposit(-result.commission)  // 수수료 음수로 입금
                        positionUseCase.syncOpenedPosition(result)
                        positionUseCase.publishPositionOpenedEvent(result)
                    } else {
                        positionUseCase.updateClosePosition(result)
                        val realizedPnL = (BigDecimal(result.price) * BigDecimal(result.amount)).toDouble()
                        accountUseCase.deposit(realizedPnL) // 수익 입금
                        positionUseCase.publishPositionClosedEvent(result)
                    }
                }
            }

            is OrderResult.Canceled -> {
                pendingOrders.remove(result.orderId)?.let {
                    orderRepository.deletePendingOrder(result.orderId)
                } ?: RuntimeException("invalid order id ${result.orderId}")
            }
        }
    }

    private fun send(order: Order) {
        pendingOrders[order.orderId] = order

        val result: SendOrderResult
        if (order is Order.CancelOrder) {
            result = try {
                exchangeImpl.cancelOrder(order.symbol, order.orderId)
                SendOrderResult.Success
            } catch (e: Exception) {
                pendingOrders.remove(order.orderId)
                SendOrderResult.Failure(e)
            }
        } else {
            result = try {
                exchangeImpl.sendOrder(order)
                SendOrderResult.Success
            } catch (e: Exception) {
                pendingOrders.remove(order.orderId)
                SendOrderResult.Failure(e)
            }
        }

        if (result is SendOrderResult.Success) {
            orderRepository.savePendingOrder(order)
        }
    }
}