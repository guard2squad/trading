package com.g2s.trading.repository

import com.g2s.trading.order.NewOrder
import com.g2s.trading.order.OrderRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

@Component
class MongoOrderRepository(
    private val mongoTemplate: MongoTemplate
) : OrderRepository {

    companion object {
        const val ORDER_COLLECTION_NAME = "pending_orders"
    }

    override fun findAllPendingOrders(): List<NewOrder> {
        return mongoTemplate.findAll(NewOrder::class.java)
    }

    override fun savePendingOrder(order: NewOrder) {
        mongoTemplate.save(order, ORDER_COLLECTION_NAME)
    }

    override fun deletePendingOrder(id: String) {
        val query = Query(Criteria.where("id").`is`(id))
        mongoTemplate.remove(query, ORDER_COLLECTION_NAME)
    }
}