package com.g2s.trading.position

import org.springframework.stereotype.Repository

@Repository
interface PositionRepository {
    fun findAllPositions(): List<NewPosition>
    fun savePosition(position: NewPosition)
    fun updatePosition(position: NewPosition)
    fun deletePosition(position: NewPosition)
}
