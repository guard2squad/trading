package com.g2s.trading.position

import com.g2s.trading.event.EventUseCase
import com.g2s.trading.order.NewCloseOrder
import com.g2s.trading.order.NewOpenOrder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class NewPositionUseCase(
    private val positionRepository: PositionRepository,
    private val eventUseCase: EventUseCase
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val openedPositions = ConcurrentHashMap<String, NewPosition>()

    init {
        loadPositions()
    }

    private fun loadPositions() {
        // db 조회
        val positions = positionRepository.findAllPositions()
        logger.debug("load positions: ${positions.size}")
        // map 저장
        positions.forEach { position ->
            openedPositions[position.id] = position
        }
    }

    fun getAllPositions(): List<NewPosition> {
        return openedPositions.values.toList()
    }

    fun openPosition(order: NewOpenOrder): NewPosition {
        val position = NewPosition(openOrder = order)
        openedPositions[position.id] = position
        return position
    }

    fun addCloseOrder(order: NewCloseOrder) {
        openedPositions[order.positionId]?.let {
            it.closeOrders[order.id] = order
        } ?: throw RuntimeException("position does not exist. positionId: ${order.positionId}")
    }

    fun updateOpenPosition(order: NewOpenOrder) {
        openedPositions[order.positionId]?.let { position ->
            position.openOrder = order

        } ?: throw RuntimeException("position does not exist. positionId: ${order.positionId}")
    }

    fun updateClosePosition(order: NewCloseOrder) {
        openedPositions[order.positionId]?.let { position ->
            position.closeOrders[order.id] ?: throw RuntimeException("close order update failed")
            position.closeOrders[order.id] = order
        } ?: throw RuntimeException("position does not exist. positionId: ${order.positionId}")
    }
}