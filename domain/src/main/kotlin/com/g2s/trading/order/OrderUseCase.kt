package com.g2s.trading.order

import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.common.ApiErrors
import com.g2s.trading.event.EventUseCase
import com.g2s.trading.event.OrderEvent
import com.g2s.trading.event.PositionEvent
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.position.PositionUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class OrderUseCase(
    private val exchangeImpl: Exchange,
    private val positionUseCase: PositionUseCase,
    private val accountUseCase: AccountUseCase,
    private val eventUseCase: EventUseCase,
    private val orderRepository: OrderRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val pendingOrders: MutableMap<String, Order> = mutableMapOf()
    private val processingOpenOrders: MutableMap<String, OpenOrder> = mutableMapOf()
    private val processingCloseOrders: MutableMap<String, CloseOrder> = mutableMapOf()

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
                            processingOpenOrders[order.orderId] = order
                            // TODO: processingOrders DB에 저장
                            positionUseCase.openPosition(order)
                        }

                        is CloseOrder -> {
                            processingCloseOrders[order.orderId] = order
                            // TODO: processingOrders DB에 저장
                            val position = positionUseCase.findPosition(order.positionId)!!
                            position.closeOrderIds.add(orderId)
                            positionUseCase.updatePosition(position)
                        }

                        else -> {}
                    }
                } ?: RuntimeException("invalid order id ${result.orderId}")

            }

            is OrderResult.FilledOrderResult.PartiallyFilled -> {
                processingOpenOrders[result.orderId]?.let {
                    val position = positionUseCase.findPosition(it.positionId)
                    position?.run {
                        // position update
                        val quoteValue = BigDecimal(this.price) * BigDecimal(this.amount) +
                                BigDecimal(result.price) * BigDecimal(result.amount)
                        this.amount += result.amount
                        this.price = (quoteValue / BigDecimal(this.amount)).toDouble()
                        positionUseCase.updatePosition(this)
                        // position update debug
                        logger.debug("포지션 양: ${this.amount}, 포지션 금액: ${this.price}")
                        // account debug
                        logger.debug("계좌 싱크 전: ${accountUseCase.getAccount()}")
                        // account sync
                        // 포지션에 할당된 금액 싱크
                        val expectedPositionValue = BigDecimal(this.expectedPrice) * BigDecimal(result.amount)
                        val actualPositionValue = BigDecimal(result.price) * BigDecimal(result.amount)
                        accountUseCase.reverseTransfer(expectedPositionValue - actualPositionValue)
                        // 수수료 차액 입금
                        val expectedCommission = expectedPositionValue * BigDecimal(this.symbol.commissionRate)
                        val actualCommission = BigDecimal(result.commission)
                        accountUseCase.deposit(expectedCommission - actualCommission)
                        logger.debug("수수료 차액: " + (expectedCommission - actualCommission))
                        // account debug
                        logger.debug("계좌 싱크 후: ${accountUseCase.getAccount()}")
                    }
                }
                processingCloseOrders[result.orderId]?.let {
                    val position = positionUseCase.findPosition(it.positionId)
                    position?.run {
                        // position update
                        this.amount -= result.amount
                        positionUseCase.updatePosition(this)
                        // position update debug
                        logger.debug("포지션 양: ${this.amount}")
                        // account debug
                        logger.debug("계좌 싱크 전: ${accountUseCase.getAccount()}")
                        // account sync
                        // 포지션에 할당된 금액 싱크
                        val closedPositionValue = (BigDecimal(this.price) * BigDecimal(result.amount))
                        accountUseCase.reverseTransfer(closedPositionValue)
                        // 수수료 차액 입금
                        val expectedCommission =
                            BigDecimal(this.expectedPrice) * BigDecimal(result.amount) * BigDecimal(this.symbol.commissionRate)
                        val actualCommission = BigDecimal(result.commission)
                        accountUseCase.deposit(expectedCommission - actualCommission)
                        logger.debug("수수료 차액: " + (expectedCommission - actualCommission))
                        // pnl 입금
                        accountUseCase.deposit(BigDecimal(result.realizedPnL))
                        // account debug
                        logger.debug("계좌 싱크 후: ${accountUseCase.getAccount()}")
                    }
                }
            }

            is OrderResult.FilledOrderResult.Filled -> {
                processingOpenOrders.remove(result.orderId)?.let {
                    val position = positionUseCase.findPosition(it.positionId)
                    position?.run {
                        // position update
                        val quoteValue = BigDecimal(this.price) * BigDecimal(this.amount) +
                                BigDecimal(result.price) * BigDecimal(result.amount)
                        this.amount += result.amount
                        this.price = (quoteValue / BigDecimal(this.amount)).toDouble()
                        positionUseCase.updatePosition(this)
                        // position update debug
                        logger.debug("포지션 양: ${this.amount}, 포지션 금액: ${this.price}")
                        // account debug
                        logger.debug("계좌 싱크 전: ${accountUseCase.getAccount()}")
                        // account sync
                        // 포지션에 할당된 금액 싱크
                        val expectedPositionValue = BigDecimal(this.expectedPrice) * BigDecimal(result.amount)
                        val actualPositionValue = BigDecimal(result.price) * BigDecimal(result.amount)
                        accountUseCase.reverseTransfer(expectedPositionValue - actualPositionValue)
                        // 수수료 차액 입금
                        val expectedCommission = expectedPositionValue * BigDecimal(this.symbol.commissionRate)
                        val actualCommission = BigDecimal(result.commission)
                        accountUseCase.deposit(expectedCommission - actualCommission)
                        logger.debug("수수료 차액: " + (expectedCommission - actualCommission))
                        // account debug
                        logger.debug("계좌 싱크 후: ${accountUseCase.getAccount()}")
                        // debug
                        if (this.amount != result.accumulatedAmount) {
                            logger.debug("로컬 포지션 양: ${this.amount}, 거래소에서 받은 누적 포지션 양: ${result.accumulatedAmount}")
                        }
                        if (this.price != result.averagePrice) {
                            logger.debug("로컬 포지션 가격: ${this.price}, 거래소에서 받은 평균 가격: ${result.averagePrice}")
                        }
                        // publish close 주문 트리거
                        val event = PositionEvent.PositionOpenedEvent(this)
                        eventUseCase.publishAsyncEvent(event)
                        // TODO: processingOrders DB에서 삭제
                    }
                }

                // close
                processingCloseOrders.remove(result.orderId)?.let {
                    val position = positionUseCase.findPosition(it.positionId)
                    position?.run {
                        this.amount -= result.amount
                        positionUseCase.updatePosition(this)
                        // position update debug
                        logger.debug("포지션 양: ${this.amount}")
                        // account debug
                        logger.debug("계좌 싱크 전: ${accountUseCase.getAccount()}")
                        // account sync
                        // 포지션에 할당된 금액 싱크
                        val closedPositionValue = (BigDecimal(this.price) * BigDecimal(result.amount))
                        accountUseCase.reverseTransfer(closedPositionValue)
                        // 수수료 차액 입금
                        val expectedCommission =
                            BigDecimal(this.expectedPrice) * BigDecimal(result.amount) * BigDecimal(this.symbol.commissionRate)
                        val actualCommission = BigDecimal(result.commission)
                        accountUseCase.deposit(expectedCommission - actualCommission)
                        logger.debug("수수료 차액: " + (expectedCommission - actualCommission))
                        // pnl 입금
                        accountUseCase.deposit(BigDecimal(result.realizedPnL))
                        // account debug
                        logger.debug("계좌 싱크 후: ${accountUseCase.getAccount()}")
                        // 포지션 삭제
                        positionUseCase.removePosition(position.positionId)
                        // publish 반대 close 주문 취소 트리거
                        val event = PositionEvent.PositionClosedEvent(Pair(position, result.orderId))
                        eventUseCase.publishAsyncEvent(event)
                        // TODO: processingOrders DB에서 삭제
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
        orderRepository.savePendingOrder(order)

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

        if (result is SendOrderResult.Failure) {
            orderRepository.deletePendingOrder(order.orderId)

            if (result.e as ApiErrors == OrderFailErrors.RETRYABLE_ERROR) {
                eventUseCase.publishAsyncEvent(OrderEvent.OrderImmediatelyTriggerEvent(order as CloseOrder))
                return
            }
        }
    }
}