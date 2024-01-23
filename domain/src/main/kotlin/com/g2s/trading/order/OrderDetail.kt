package com.g2s.trading.order

sealed class OrderDetail {
    data class SimpleOrderDetail(val orderSide: OrderSide, val orderType: OrderType) :
        OrderDetail()

}
