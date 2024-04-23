package com.g2s.trading.history

import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.Position

data class OpenHistory(
    val historyKey: String,
    val position: Position,
    val strategyKey: String,
    val openCondition: OpenCondition,
    val orderSide: OrderSide,
    val orderType: OrderType,
    val transactionTime: Long,
    val commission: Double,
    val afterBalance: Double,
) {
    companion object {
        fun generateHistoryKey(position: Position): String {
            return "${position.positionKey}-${position.strategyKey}-${position.openTime}"
        }
    }
}
