package com.g2s.trading.position

import com.g2s.trading.order.NewCloseOrder
import org.springframework.stereotype.Repository

@Repository
interface PositionRepository {
    fun findAllPositions(): List<NewPosition>
    fun savePosition(position: NewPosition)
    fun updateCloseOrders(id: String, newCloseOrders: List<NewCloseOrder>)
    fun deletePosition(id: String)
}
