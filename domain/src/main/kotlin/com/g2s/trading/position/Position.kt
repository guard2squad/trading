package com.g2s.trading.position

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.order.Symbol

data class Position(
    val symbol: Symbol,
    val entryPrice: Double,
    val positionAmt: Double
) {
    lateinit var orderSide: OrderSide
    lateinit var orderType: OrderType
    lateinit var referenceData: JsonNode
}
