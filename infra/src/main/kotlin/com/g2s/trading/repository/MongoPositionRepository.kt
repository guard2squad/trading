package com.g2s.trading.repository

import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionRepository
import org.springframework.data.mongodb.core.MongoTemplate

import org.springframework.stereotype.Component

@Component
class MongoPositionRepository(val mongoTemplate: MongoTemplate) : PositionRepository {

    companion object {
        private const val POSITION_COLLECTION_NAME = "positions"
    }

    override fun findAllPositions(): List<Position> {
        return mongoTemplate.findAll(Position::class.java, POSITION_COLLECTION_NAME)
    }

    override fun savePosition(position: Position) {
        mongoTemplate.save(position, POSITION_COLLECTION_NAME)
    }

    override fun deletePosition(position: Position) {
        mongoTemplate.remove(position, POSITION_COLLECTION_NAME)
    }
}
