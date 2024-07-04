package com.g2s.trading.position

import com.g2s.trading.order.OpenOrder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PositionUseCase(
    private val positionRepository: PositionRepository,
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val openedPositions = ConcurrentHashMap<String, Position>()

    init {
        loadPositions()
    }

    private fun loadPositions() {
        // db 조회
        val positions = positionRepository.findAllPositions()
        logger.info("load positions: ${positions.size}")
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
            expectedEntryPrice = order.entryPrice,
            expectedQuantity = order.quantity,
            openOrderId = order.orderId,
            closeOrderIds = mutableSetOf()
        )
        order.positionId = position.positionId
        openedPositions[position.positionId] = position
        positionRepository.savePosition(position)
        return position
    }

    fun updatePosition(position: Position) {
        positionRepository.updatePosition(position)
    }

    fun findPosition(positionId: String): Position? {
        return openedPositions[positionId]
    }

    fun removePosition(positionId: String) {
        openedPositions.remove(positionId)
        positionRepository.deletePosition(positionId)
    }
}