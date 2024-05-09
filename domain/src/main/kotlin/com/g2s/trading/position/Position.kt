package com.g2s.trading.position

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.account.Asset
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.symbol.Symbol
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class Position(
    val positionKey: PositionKey,
    val strategyKey: String,
    val symbol: Symbol,
    val entryPrice: Double,
    val positionAmt: Double,
    val orderSide: OrderSide,
    val orderType: OrderType,
    val referenceData: JsonNode,
    val openTime: String = getCurrentUtcDateTimeString(),
    val asset: Asset,
    val synced: Boolean = false // close시 확인
) {

    // ONE_WAY_MODE의 경우 Symbol로 만 식별 가능하지만 HEDGE_MODE인 경우 OrderSide도 포함해야함
    data class PositionKey(val symbol: Symbol, val orderSide: OrderSide) {
        override fun toString(): String {
            return "symbol=${symbol.value}/orderSide=$orderSide"
        }
    }

    companion object {
        fun update(old: Position, refreshData: PositionRefreshData): Position {
            return old.copy(
                entryPrice = refreshData.entryPrice,
                positionAmt = refreshData.positionAmt,
            )
        }

        fun sync(old: Position): Position {
            return old.copy(
                synced = true
            )
        }

        private fun getCurrentUtcDateTimeString(): String {
            val nowSeoul = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
            val formatter = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss")

            return nowSeoul.format(formatter)
        }
    }
}
