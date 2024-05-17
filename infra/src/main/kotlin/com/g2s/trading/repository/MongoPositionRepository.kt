package com.g2s.trading.repository

import com.g2s.trading.order.NewCloseOrder
import com.g2s.trading.position.NewPosition
import com.g2s.trading.position.PositionRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

@Component
class MongoPositionRepository(private val mongoTemplate: MongoTemplate) : PositionRepository {

    companion object {
        private const val POSITION_COLLECTION_NAME = "positions"
    }

    override fun findAllPositions(): List<NewPosition> {
        return mongoTemplate.findAll(NewPosition::class.java, POSITION_COLLECTION_NAME)
    }

    override fun savePosition(position: NewPosition) {
        mongoTemplate.save(position, POSITION_COLLECTION_NAME)
    }

    override fun updateCloseOrders(id: String, newCloseOrders: List<NewCloseOrder>) {
        val query = Query(Criteria.where("id").`is`(id))
        val update = Update().set("closeOrders", newCloseOrders)

        mongoTemplate.findAndModify(query, update, NewPosition::class.java, POSITION_COLLECTION_NAME)
    }

    override fun deletePosition(id: String) {
        val query = Query(Criteria.where("id").`is`(id))
        mongoTemplate.remove(query, POSITION_COLLECTION_NAME)
    }
}
