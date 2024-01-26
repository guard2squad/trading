package com.g2s.trading.order

import com.g2s.trading.Symbol

sealed class OrderDetail {
    data class SimpleOrderDetail(
        val symbol: Symbol,
        val currentPrice: Double,
        val orderSide: OrderSide,
        val orderType: OrderType
    ) :
        OrderDetail()

}
