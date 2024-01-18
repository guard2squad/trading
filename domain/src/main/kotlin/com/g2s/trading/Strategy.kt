package com.g2s.trading

interface Strategy {

    fun shouldOpen(indicator: Indicator): Boolean
    fun shouldClose(position: Position): Boolean
    fun hasAvailableBalance(account: Account): Boolean
    fun orderSide(indicator: Indicator): OrderSide
    fun makeOrder(symbol: Symbols, orderType: OrderType, orderSide: OrderSide, quantity: Double): Order

}
