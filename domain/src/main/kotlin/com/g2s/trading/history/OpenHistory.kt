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
    val transactionTime: Long = 0,
    val commission: Double = 0.0,
    val afterBalance: Double = 0.0,
) {
    companion object {
        fun generateHistoryKey(position: Position): String {
            return "${position.positionKey}-${position.strategyKey}-${position.openTime}"
        }
    }
}
