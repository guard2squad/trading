package com.g2s.trading.order

import org.springframework.stereotype.Repository

@Repository
interface OrderRepository {
    fun findAllPendingOrders(): List<Order>
    fun savePendingOrder(order: Order)
    fun deletePendingOrder(id: String)
}