package com.g2s.trading.position

import org.springframework.stereotype.Repository

@Repository
interface PositionRepository {
    fun findAllPositions(): List<Position>

    fun savePosition(position: Position)

    fun deletePosition(position: Position)
}