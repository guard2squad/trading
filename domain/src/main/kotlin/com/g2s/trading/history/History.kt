package com.g2s.trading.history

import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.Position

sealed class History() {

    // TODO : Open과 Close를 pairing 할 수 있는 HistoryKey가 필요함.
    // Position 정보로 Key genereate

    data class Open(
        val position: Position,
        val strategyKey: String,
        val openCondition: OpenCondition,
        val orderSide: OrderSide,
        val orderType: OrderType,
        val transactionTime: Long,
        val commission: Double,
        val balance: Double,    // 주문 완료 후 잔고
    ) : History()

    data class Close(
        val position: Position,
        val strategyKey: String,
        val closeCondition: CloseCondition,
        val transactionTime: Long,
        val orderSide: OrderSide,
        val orderType: OrderType,
        val realizedPnL: Double,
        val commission: Double,
        val balance: Double,    // 주문 완료 후 잔고
    ) : History()

    data class HistoryKey(val value: String) {
        companion object {
            fun generate(position: Position): HistoryKey {
                // TODO : position을 기반으로 keygenerate
                return HistoryKey("TODO")
            }
        }
    }
}
