package com.g2s.trading.position

import com.g2s.trading.order.NewCloseOrder
import com.g2s.trading.order.NewOpenOrder
import java.util.UUID

data class NewPosition(
    val id: String = UUID.randomUUID().toString(),
    val openOrder: NewOpenOrder,
    val closeOrders: MutableList<NewCloseOrder> = mutableListOf()
)
