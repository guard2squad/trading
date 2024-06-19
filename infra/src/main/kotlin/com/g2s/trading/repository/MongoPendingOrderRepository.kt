package com.g2s.trading.repository

import com.g2s.trading.order.Order
import com.g2s.trading.order.PendingOrderRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

@Component
class MongoPendingOrderRepository(
    private val mongoTemplate: MongoTemplate
) : PendingOrderRepository {

    companion object {
        const val ORDER_COLLECTION_NAME = "pending_orders"
    }

    override fun findAllOrders(): List<Order> {
        return mongoTemplate.findAll(Order::class.java)
    }

    override fun saveOrder(order: Order) {
        mongoTemplate.save(order, ORDER_COLLECTION_NAME)
    }

    override fun deleteOrder(id: String) {
        val query = Query(Criteria.where("orderId").`is`(id))
        mongoTemplate.remove(query, ORDER_COLLECTION_NAME)
    }
}