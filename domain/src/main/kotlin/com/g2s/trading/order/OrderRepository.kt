package com.g2s.trading.order

import org.springframework.stereotype.Repository

@Repository
interface OrderRepository {
    fun findAllPendingOrders(): List<NewOrder>
    fun savePendingOrder(order: NewOrder)
    fun deletePendingOrder(id: String)
}