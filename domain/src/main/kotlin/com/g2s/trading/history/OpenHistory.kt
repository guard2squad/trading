package com.g2s.trading.history

import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.Position
import java.security.MessageDigest

data class OpenHistory(
    val historyKey: String,
    val position: Position,
    val strategyKey: String,
    val openCondition: OpenCondition? = null,
    val orderSide: OrderSide,
    val orderType: OrderType,
    val binanceOrderId: Long,
    val transactionTime: Long = 0,
    val commission: Double = 0.0,
    val afterBalance: Double = 0.0,
    val syncedQuoteQty: Double = 0.0,   // quoted quantity: 거래에서 가격(price)과 거래 수량(quantity)을 곱한 값
) {
    companion object {
        fun generateHistoryKey(position: Position): String {
            val input = "${position.positionKey}/${position.strategyKey}/${position.openTime}"

            return hashString(input)
        }

        private fun hashString(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString(separator = "") { "%02x".format(it) }
        }
    }
}
