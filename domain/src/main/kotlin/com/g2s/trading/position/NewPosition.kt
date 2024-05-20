package com.g2s.trading.position

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.order.OrderSide
import com.g2s.trading.symbol.Symbol
import java.util.*

data class NewPosition(
    val id: String = UUID.randomUUID().toString(),
    val symbol: Symbol,
    val side: OrderSide,
    val referenceData: JsonNode,
    val closeOrders: Set<String>
) {
    var price: Double = 0.0
    var amount: Double = 0.0
}
