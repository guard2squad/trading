package com.g2s.trading.exchange

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.indicator.MarkPrice
import com.g2s.trading.position.PositionMode
import com.g2s.trading.symbol.Symbol
import com.g2s.trading.order.NewOrder
import com.g2s.trading.account.NewAccount

interface Exchange {
    fun sendOrder(order: NewOrder)
    fun cancelOrder(symbol: Symbol, orderId: String)
    fun getAccount(): NewAccount

    /***
     *
     * 절 취 선
     *
     */
    fun closePosition(position: Position, orderType: OrderType, price: Double = 0.0): Long
    fun openPosition(position: Position): Long
    fun setPositionMode(positionMode: PositionMode)
    fun getMarkPrice(symbol: Symbol): MarkPrice
    fun getPosition(symbol: Symbol): Position
    fun getQuantityPrecision(symbol: Symbol): Int
    fun getMinQty(symbol: Symbol): Double
    fun getMinNotionalValue(symbol: Symbol): Double
    fun getLeverage(symbol: Symbol): Int
    fun setLeverage(symbol: Symbol, leverage: Int): Int
    fun getHistoryInfo(position: Position, orderId: Long): JsonNode?
    fun getCurrentBalance(timeStamp: Long): Double
    fun getCommissionRate(symbol: Symbol): Double
    fun getPricePrecision(symbol: Symbol): Int
    fun getMinPrice(symbol: Symbol): Double
    fun getTickSize(symbol: Symbol): Double
    fun cancelOrder(symbol: Symbol, orderId: Long)


}
