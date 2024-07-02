package com.g2s.trading.order

import com.g2s.trading.symbol.Symbol

sealed class OrderResult {
    abstract val orderId: String

    data class New(
        override val orderId: String
    ) : OrderResult()

    data class Canceled(
        override val orderId: String
    ) : OrderResult()

    sealed class FilledOrderResult : OrderResult() {

        abstract val symbol: Symbol
        abstract val price: Double
        abstract val quantity: Double
        abstract val commission: Double
        abstract val realizedPnL: Double
        abstract val averagePrice: Double
        abstract val accumulatedQuantity: Double

        data class PartiallyFilled(
            override val orderId: String,
            override val symbol: Symbol,
            override val price: Double,
            override val quantity: Double,
            override val commission: Double,
            override val realizedPnL: Double,
            override val averagePrice: Double,
            override val accumulatedQuantity: Double,
        ) : FilledOrderResult()

        data class Filled(
            override val orderId: String,
            override val symbol: Symbol,
            override val price: Double,
            override val quantity: Double,
            override val commission: Double,
            override val realizedPnL: Double,
            override val averagePrice: Double,
            override val accumulatedQuantity: Double,
        ) : FilledOrderResult()
    }
}