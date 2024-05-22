package com.g2s.trading.position

import com.g2s.trading.event.EventUseCase
import com.g2s.trading.event.PositionEvent
import com.g2s.trading.order.CloseOrder
import com.g2s.trading.order.OpenOrder
import com.g2s.trading.order.OrderResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

@Service
class PositionUseCase(
    private val positionRepository: PositionRepository,
    private val eventUseCase: EventUseCase
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val openedPositions = ConcurrentHashMap<String, Position>()

    init {
        loadPositions()
    }

    private fun loadPositions() {
        // db 조회
        val positions = positionRepository.findAllPositions()
        logger.debug("load positions: ${positions.size}")
        // map 저장
        positions.forEach { position ->
            openedPositions[position.positionId] = position
        }
    }

    fun getAllPositions(): List<Position> {
        return openedPositions.values.toList()
    }

    fun openPosition(order: OpenOrder): Position {
        val position = Position(
            symbol = order.symbol,
            side = order.side,
            referenceData = order.referenceData,
            openOrder = order,
            closeOrderIds = mutableSetOf()
        )
        order.positionId = position.positionId
        openedPositions[position.positionId] = position
        positionRepository.savePosition(position)
        return position
    }

    fun addCloseOrder(order: CloseOrder) {
        openedPositions[order.positionId]?.let {
            it.closeOrderIds.add(order.orderId)
            positionRepository.updatePosition(it)
        } ?: throw RuntimeException("position does not exist. positionId: ${order.positionId}")

    }

    fun updateOpenedPosition(orderResult: OrderResult.FilledOrderResult) {
        openedPositions.values.firstOrNull { position ->
            position.openOrder.orderId == orderResult.orderId
        }?.apply {
            val quoteValue = BigDecimal(this.price) * BigDecimal(this.amount) +
                    BigDecimal(orderResult.price) * BigDecimal(orderResult.amount)
            this.amount += orderResult.amount
            this.price = (quoteValue / BigDecimal(this.amount)).toDouble()
            logger.debug("OrderId[${orderResult.orderId}] 계산 된 평균가격: " + this.price)
            positionRepository.updatePosition(this)
        }
    }

    fun syncOpenedPosition(orderResult: OrderResult.FilledOrderResult.Filled) {
        openedPositions.values.firstOrNull { position ->
            position.openOrder.orderId == orderResult.orderId
        }?.apply {
            if (this.amount != orderResult.accumulatedAmount) {
                this.amount = orderResult.accumulatedAmount
            }
            if (this.price != orderResult.averagePrice) {
                this.price = orderResult.averagePrice
            }
            positionRepository.updatePosition(this)
        }
    }

    fun findOpenedPosition(orderId: String): Pair<Position, Boolean>? {
        openedPositions.values.firstOrNull() {
            it.openOrder.orderId == orderId || it.closeOrderIds.contains(orderId)
        }?.let { position ->
            val isOpen = position.openOrder.orderId == orderId
            return Pair(position, isOpen)
        } ?: return null
    }

    fun publishPositionOpenedEvent(orderResult: OrderResult.FilledOrderResult.Filled) {
        openedPositions.values.firstOrNull { position -> position.openOrder.orderId == orderResult.orderId }
            ?.also { position ->
                val event = PositionEvent.PositionOpenedEvent(position)
                eventUseCase.publishAsyncEvent(event)
            } ?: throw RuntimeException("position does not exist. positionId: ${orderResult.orderId}")
    }

    fun updateClosePosition(orderResult: OrderResult.FilledOrderResult) {
        openedPositions.values.firstOrNull { position ->
            position.closeOrderIds.contains(orderResult.orderId)
        }?.apply {
            this.amount -= orderResult.amount
            positionRepository.updatePosition(this)
        }
    }

    fun publishPositionClosedEvent(orderResult: OrderResult.FilledOrderResult.Filled) {
        openedPositions.values.firstOrNull { position -> position.closeOrderIds.contains(orderResult.orderId) }
            ?.also { position ->
                val event = PositionEvent.PositionClosedEvent(Pair(position, orderResult.orderId))
                eventUseCase.publishAsyncEvent(event)
                openedPositions.remove(position.positionId)
                positionRepository.deletePosition(position.positionId)
            } ?: throw RuntimeException("position does not exist. positionId: ${orderResult.orderId}")
    }
}