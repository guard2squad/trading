package com.g2s.trading.position

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.order.Symbol

data class Position(
    val strategyKey: String,
    val symbol: Symbol,
    val entryPrice: Double,
    val positionAmt: Double,
    val orderSide: OrderSide,
    val orderType: OrderType,
    val referenceData: JsonNode,
    val synced: Boolean = false // close시 확인
) {
    companion object {
        fun update(old: Position, refreshData: PositionRefreshData): Position {
            return old.copy(
                entryPrice = refreshData.entryPrice,
                positionAmt = refreshData.positionAmt,
                synced = true
            )
        }
    }
}

