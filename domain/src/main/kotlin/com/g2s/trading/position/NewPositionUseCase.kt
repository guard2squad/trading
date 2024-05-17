package com.g2s.trading.position

import com.g2s.trading.event.EventUseCase
import com.g2s.trading.event.NewPositionEvent
import com.g2s.trading.order.NewCloseOrder
import com.g2s.trading.order.NewOpenOrder
import com.g2s.trading.order.OrderResult
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

    fun addCloseOrder(order: NewCloseOrder) {
        val position = openedPositions[order.positionId]
        position!!.closeOrders.add(order)
        // TODO("UPDATE POSITION")
        positionRepository.updateCloseOrders(position.id, position.closeOrders)
    }

    fun removeCloseOrder(order: NewCloseOrder) {
        val position = openedPositions[order.positionId]
        position!!.closeOrders.remove(order)
        // TODO("UPDATE POSITION")
        positionRepository.updateCloseOrders(position.id, position.closeOrders)
    }

    fun openPosition(pendingOrder: NewOpenOrder.MarketOrder, completeOrder: OrderResult) {
        val position = NewPosition(
            openOrder = pendingOrder.copy(
                price = completeOrder.price,
                amount = completeOrder.amount
            )
        )

        openedPositions[position.id] = position
        // TODO("SAVE POSITION")
        positionRepository.savePosition(position)
        eventUseCase.publishEvent(NewPositionEvent.PositionOpenedEvent(position))
        TODO("history")
    }

    fun closePosition(positionId: String): NewPosition? {
        val position = openedPositions.remove(positionId)
        position?.let {
            // TODO("DELETE POSITION")
            positionRepository.deletePosition(position.id)
            eventUseCase.publishEvent(NewPositionEvent.PositionClosedEvent(position))
        }
        TODO("history")

        return position
    }
}