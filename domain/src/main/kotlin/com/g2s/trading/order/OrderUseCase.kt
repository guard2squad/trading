package com.g2s.trading.order

import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.common.ApiError
import com.g2s.trading.event.EventUseCase
import com.g2s.trading.event.OrderEvent
import com.g2s.trading.event.PositionEvent
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.tradingHistory.TradingHistoryUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class OrderUseCase(
    private val exchangeImpl: Exchange,
    private val positionUseCase: PositionUseCase,
    private val accountUseCase: AccountUseCase,
    private val eventUseCase: EventUseCase,
    private val tradingHistoryUseCase: TradingHistoryUseCase,
    private val pendingOrderRepository: PendingOrderRepository,
    private val processingOrderRepository: ProcessingOrderRepository
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
                    pendingOrderRepository.deleteOrder(result.orderId)
                    when (order) {
                        is OpenOrder -> {
                            processingOpenOrders[order.orderId] = order
                            processingOrderRepository.saveOrder(order)
                            positionUseCase.openPosition(order)
                        }

                        is CloseOrder -> {
                            processingCloseOrders[order.orderId] = order
                            processingOrderRepository.saveOrder(order)
                            val position = positionUseCase.findPosition(order.positionId)!!
                            position.closeOrderIds.add(orderId)
                            positionUseCase.updatePosition(position)
                        }

                        else -> {}
                    }
                } ?: RuntimeException("invalid order id ${result.orderId}")

            }

            is OrderResult.FilledOrderResult.PartiallyFilled -> {
                processingOpenOrders[result.orderId]?.let { order ->
                    val position = positionUseCase.findPosition(order.positionId)
                    position?.run {
                        logger.info("OPEN ORDER PARTIALLY FILLED 전 포지션 양: ${this.quantity}, 포지션 금액: ${this.price}")
                        logger.info("OPEN ORDER PARTIALLY FILLED 전 계좌: " + accountUseCase.getAccount().toString())
                        // debug
                        accountUseCase.printAccount()
                        // position update
                        this.quantity = result.accumulatedQuantity
                        this.price = result.averagePrice
                        this.fee += result.commission
                        positionUseCase.updatePosition(this)
                        // account sync
                        // 포지션에 할당된 금액 싱크
                        val expectedPositionValue = BigDecimal(this.expectedEntryPrice) * BigDecimal(result.quantity)
                        val actualPositionValue = BigDecimal(result.price) * BigDecimal(result.quantity)
                        // 레버리지로 나눠서 포지션 마진 차이 입금
                        accountUseCase.transferToAvailable(
                            (expectedPositionValue - actualPositionValue)
                                .divide(
                                    BigDecimal(this.symbol.leverage),
                                    this.symbol.quotePrecision,
                                    RoundingMode.HALF_UP
                                )
                        )
                        // 수수료 차액 입금
                        val expectedCommission = expectedPositionValue * BigDecimal(this.symbol.commissionRate)
                        val actualCommission = BigDecimal(result.commission)
                        accountUseCase.deposit(expectedCommission - actualCommission)
                        logger.info("OPEN ORDER PARTIALLY FILLED 후 포지션 양: ${this.quantity}, 포지션 금액: ${this.price}")
                        logger.info("OPEN ORDER PARTIALLY FILLED 후 계좌: " + accountUseCase.getAccount().toString())
                        // debug
                        accountUseCase.printAccount()
                    }
                }
                processingCloseOrders[result.orderId]?.let { order ->
                    val position = positionUseCase.findPosition(order.positionId)
                    position?.run {
                        logger.info("CLOSE ORDER PARTIALLY FILLED 전 포지션 양: ${this.quantity}, 포지션 금액: ${this.price}")
                        logger.info("CLOSE ORDER PARTIALLY FILLED 전 계좌: " + accountUseCase.getAccount().toString())
                        // debug
                        accountUseCase.printAccount()
                        // position update
                        this.setClosePrice(result.price, result.quantity)
                        this.quantity -= result.quantity
                        this.fee += result.commission
                        this.pnl += result.realizedPnL
                        positionUseCase.updatePosition(this)
                        // account sync
                        // 포지션에 할당된 금액 싱크
                        val closedPositionValue = (BigDecimal(this.price) * BigDecimal(result.quantity))
                        // 레버리지로 나눠서 포지션 마진 차이 입금
                        accountUseCase.transferToAvailable(
                            closedPositionValue.divide(
                                BigDecimal(this.symbol.leverage),
                                this.symbol.quotePrecision,
                                RoundingMode.HALF_UP
                            )
                        )
                        // 수수료 차액 입금
                        val expectedCommission =
                            BigDecimal(this.expectedEntryPrice) * BigDecimal(result.quantity) * BigDecimal(this.symbol.commissionRate)
                        val actualCommission = BigDecimal(result.commission)
                        accountUseCase.deposit(expectedCommission - actualCommission)
                        // pnl 입금
                        accountUseCase.deposit(BigDecimal(result.realizedPnL))
                        logger.info("CLOSE ORDER PARTIALLY FILLED 후 포지션 양: ${this.quantity}, 포지션 금액: ${this.price}")
                        logger.info("CLOSE ORDER PARTIALLY FILLED 후 계좌: " + accountUseCase.getAccount().toString())
                        // debug
                        accountUseCase.printAccount()
                    }
                }
            }

            is OrderResult.FilledOrderResult.Filled -> {
                processingOpenOrders.remove(result.orderId)?.let { order ->
                    val position = positionUseCase.findPosition(order.positionId)
                    position?.run {
                        logger.info("OPEN ORDER 완전히 FILLED 전 포지션 양: ${this.quantity}, 포지션 금액: ${this.price}")
                        logger.info("OPEN ORDER 완전히 FILLED 전 계좌: " + accountUseCase.getAccount().toString())
                        // debug
                        accountUseCase.printAccount()
                        // position update
                        this.quantity = result.accumulatedQuantity
                        this.price = result.averagePrice
                        this.fee += result.commission
                        positionUseCase.updatePosition(this)
                        // account sync
                        // 포지션에 할당된 금액 싱크
                        val expectedPositionValue = BigDecimal(this.expectedEntryPrice) * BigDecimal(result.quantity)
                        val actualPositionValue = BigDecimal(result.price) * BigDecimal(result.quantity)
                        accountUseCase.transferToAvailable(
                            (expectedPositionValue - actualPositionValue).divide(
                                BigDecimal(this.symbol.leverage),
                                this.symbol.quotePrecision,
                                RoundingMode.HALF_UP
                            )
                        )
                        // 수수료 차액 입금
                        val expectedCommission = expectedPositionValue * BigDecimal(this.symbol.commissionRate)
                        val actualCommission = BigDecimal(result.commission)
                        accountUseCase.deposit(expectedCommission - actualCommission)
                        // publish close 주문 트리거
                        val event = PositionEvent.PositionOpenedEvent(this)
                        eventUseCase.publishAsyncEvent(event)
                        logger.info("OPEN ORDER 완전히 FILLED 후 포지션 양: ${this.quantity}, 포지션 금액: ${this.price}")
                        logger.info("OPEN ORDER 완전히 FILLED 후 계좌: " + accountUseCase.getAccount().toString())
                        // debug
                        accountUseCase.printAccount()
                    }
                    processingOrderRepository.deleteOrder(order.orderId)
                }

                // close
                processingCloseOrders.remove(result.orderId)?.let { order ->
                    val position = positionUseCase.findPosition(order.positionId)
                    position?.run {
                        logger.info("CLOSE ORDER 완전히 FILLED 전 계좌: " + accountUseCase.getAccount().toString())
                        // debug
                        accountUseCase.printAccount()
                        // position update
                        this.setClosePrice(result.price, result.quantity)
                        this.quantity -= result.quantity
                        this.fee += result.commission
                        this.pnl += result.realizedPnL
                        positionUseCase.updatePosition(this)
                        // account sync
                        // 포지션에 할당된 금액 싱크
                        val closedPositionValue = (BigDecimal(this.price) * BigDecimal(result.quantity))
                        accountUseCase.transferToAvailable(
                            closedPositionValue.divide(
                                BigDecimal(this.symbol.leverage),
                                this.symbol.quotePrecision,
                                RoundingMode.HALF_UP
                            )
                        )
                        // 수수료 차액 입금
                        val expectedCommission =
                            BigDecimal(this.expectedEntryPrice) * BigDecimal(result.quantity) * BigDecimal(this.symbol.commissionRate)
                        val actualCommission = BigDecimal(result.commission)
                        accountUseCase.deposit(expectedCommission - actualCommission)
                        // pnl 입금
                        accountUseCase.deposit(BigDecimal(result.realizedPnL))
                        // 히스토리 저장
                        tradingHistoryUseCase.saveHistory(position)
                        // 포지션 삭제
                        positionUseCase.removePosition(position.positionId)
                        // publish 반대 close 주문 취소 트리거
                        val event = PositionEvent.PositionClosedEvent(Pair(position, result.orderId))
                        eventUseCase.publishAsyncEvent(event)
                        logger.info("CLOSE ORDER 완전히 FILLED 후 계좌: " + accountUseCase.getAccount().toString())
                        // debug
                        accountUseCase.printAccount()
                    }
                    processingOrderRepository.deleteOrder(order.orderId)
                }
            }

            is OrderResult.Canceled -> {
                // 로컬에서 요청한 CANCEL 주문이 접수 됨
                pendingOrders.remove(result.orderId)?.let {
                    pendingOrderRepository.deleteOrder(result.orderId)
                } ?: RuntimeException("invalid order id ${result.orderId}")
                // FILLED or CANCELLED 로 상태가 변하기를 기다리던 주문 제거
                processingCloseOrders.remove(result.orderId)?.let {
                    processingOrderRepository.deleteOrder(result.orderId)
                } ?: RuntimeException("invalid order id ${result.orderId}")
            }
        }
    }

    private fun send(order: Order) {
        pendingOrders[order.orderId] = order
        pendingOrderRepository.saveOrder(order)

        val result: SendOrderResult
        if (order is Order.CancelOrder) {   // 주문 취소
            result = try {
                exchangeImpl.cancelOrder(order.symbol, order.orderId)
                SendOrderResult.Success
            } catch (e: Exception) {
                pendingOrders.remove(order.orderId)
                SendOrderResult.Failure(e)
            }
        } else {    // 주문
            result = try {
                exchangeImpl.sendOrder(order)
                SendOrderResult.Success
            } catch (e: Exception) {
                pendingOrders.remove(order.orderId)
                SendOrderResult.Failure(e)
            }
        }

        if (result is SendOrderResult.Failure) {
            pendingOrderRepository.deleteOrder(order.orderId)

            if (result.e is ApiError && result.e.code == OrderFailErrors.ORDER_IMMEDIATELY_TRIGGERED_ERROR.code) {
                eventUseCase.publishAsyncEvent(OrderEvent.OrderImmediatelyTriggerEvent(order as CloseOrder))
                return
            }
        }
    }
}