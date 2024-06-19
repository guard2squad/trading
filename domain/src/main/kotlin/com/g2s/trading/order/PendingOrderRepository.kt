package com.g2s.trading.order

import org.springframework.stereotype.Repository

@Repository
interface PendingOrderRepository {
    fun findAllOrders(): List<Order>
    fun saveOrder(order: Order)
    fun deleteOrder(id: String)
}