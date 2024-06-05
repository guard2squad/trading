package com.g2s.trading.repository

import com.g2s.trading.order.Order
import com.g2s.trading.order.ProcessingOrderRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

@Component
class MongoProcessingOrderRepository(
    private val mongoTemplate: MongoTemplate
) : ProcessingOrderRepository {
    companion object {
        const val ORDER_COLLECTION_NAME = "processing_orders"
    }

    override fun saveOrder(order: Order) {
        mongoTemplate.save(order, ORDER_COLLECTION_NAME)
    }

    override fun deleteOrder(id: String) {
        val query = Query(Criteria.where("orderId").`is`(id))
        mongoTemplate.remove(query, ORDER_COLLECTION_NAME)
    }
}

