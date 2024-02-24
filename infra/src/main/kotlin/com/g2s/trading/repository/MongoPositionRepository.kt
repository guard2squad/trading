package com.g2s.trading.repository

import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionRepository
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
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

    override fun updatePosition(position: Position) {
        mongoTemplate.db.getCollection(POSITION_COLLECTION_NAME).replaceOne(
            Filters.eq(Position::strategyKey.name, position.strategyKey),
            Document.parse(ObjectMapperProvider.get().writeValueAsString(position)),
            ReplaceOptions().upsert(true)
        )
    }

    override fun deletePosition(position: Position) {
        mongoTemplate.remove(Query.query(where("strategyKey").`is`(position.strategyKey)), POSITION_COLLECTION_NAME)
    }
}
