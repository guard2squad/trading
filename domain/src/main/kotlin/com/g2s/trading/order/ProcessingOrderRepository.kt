package com.g2s.trading.order

import org.springframework.stereotype.Repository

@Repository
interface ProcessingOrderRepository {
    fun saveOrder(order: Order)
    fun deleteOrder(id: String)
}