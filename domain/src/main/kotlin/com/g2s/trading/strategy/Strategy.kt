package com.g2s.trading.strategy

interface Strategy {
    fun invoke()

//    fun shouldOpen(indicator: Indicator): Boolean
//    fun shouldClose(position: Position): Boolean
//    fun hasAvailableBalance(account: Account): Boolean
//    fun orderSide(indicator: Indicator): OrderSide
//    fun makeOrder(symbol: Symbol, orderType: OrderType, orderSide: OrderSide, quantity: Double): Order

}
