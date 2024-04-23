package com.g2s.trading.history

import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.Position

data class CloseHistory(
    val historyKey: String,
    val position: Position,
    val strategyKey: String,
    val closeCondition: CloseCondition,
    val orderSide: OrderSide,
    val orderType: OrderType,
    val transactionTime: Long,
    val realizedPnL: Double,
    val commission: Double,
    val afterBalance: Double,
) {
    companion object {
        fun generateHistoryKey(position: Position): String {
            return "${position.positionKey}-${position.strategyKey}-${position.openTime}"
        }
    }
}
