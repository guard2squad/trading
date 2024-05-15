package com.g2s.trading.order

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.symbol.Symbol
import java.util.*

sealed class NewOrder {
    abstract val id: String
    abstract val symbol: Symbol
    abstract val price: Double
    abstract val amount: Double
    abstract val side: OrderSide
}

sealed class NewOpenOrder : NewOrder() {
    abstract val referenceData: JsonNode
    data class MarketOrder(
        override val id: String = UUID.randomUUID().toString(),
        override val symbol: Symbol,
        override val price: Double,
        override val amount: Double,
        override val side: OrderSide,
        override val referenceData: JsonNode
    ) : NewOpenOrder()
}

sealed class NewCloseOrder : NewOrder() {
    abstract val positionId: String

    data class NewTakeProfitOrder(
        override val id: String = UUID.randomUUID().toString(),
        override val symbol: Symbol,
        override val price: Double,
        override val amount: Double,
        override val side: OrderSide,
        override val positionId: String
    ) : NewCloseOrder()

    data class NewStopLossOrder(
        override val id: String = UUID.randomUUID().toString(),
        override val symbol: Symbol,
        override val price: Double,
        override val amount: Double,
        override val side: OrderSide,
        override val positionId: String
    ) : NewCloseOrder()
}



