package com.g2s.trading.order

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.symbol.Symbol
import java.util.*

sealed class Order {
    abstract val orderId: String
    abstract val symbol: Symbol

    data class CancelOrder(
        override val orderId: String,
        override val symbol: Symbol
    ) : Order()
}

sealed class OpenOrder : Order() {
    abstract val quantity: Double
    abstract val side: OrderSide
    abstract val entryPrice: Double
    abstract val referenceData: JsonNode
    lateinit var positionId: String

    data class MarketOrder(
        override val orderId: String = UUID.randomUUID().toString(),
        override val symbol: Symbol,
        override val quantity: Double,
        override val side: OrderSide,
        override val entryPrice: Double,    // 예상 진입 가격
        override val referenceData: JsonNode,
    ) : OpenOrder()
}

sealed class CloseOrder : Order() {
    abstract val price: Double
    abstract val quantity: Double
    abstract val side: OrderSide
    abstract val positionId: String

    data class TakeProfitOrder(
        override val orderId: String = UUID.randomUUID().toString(),
        override val symbol: Symbol,
        override val price: Double,
        override val quantity: Double,
        override val side: OrderSide,
        override val positionId: String
    ) : CloseOrder()

    data class StopLossOrder(
        override val orderId: String = UUID.randomUUID().toString(),
        override val symbol: Symbol,
        override val price: Double,
        override val quantity: Double,
        override val side: OrderSide,
        override val positionId: String
    ) : CloseOrder()

    data class MarketOrder(
        override val orderId: String = UUID.randomUUID().toString(),
        override val symbol: Symbol,
        override val price: Double,
        override val quantity: Double,
        override val side: OrderSide,
        override val positionId: String
    ) : CloseOrder()
}