package com.g2s.trading.exchange

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.indicator.MarkPrice
import com.g2s.trading.position.PositionMode
import com.g2s.trading.symbol.Symbol
import com.g2s.trading.order.Order
import com.g2s.trading.account.Account

interface Exchange {
    fun sendOrder(order: Order)
    fun cancelOrder(symbol: Symbol, orderId: String)
    fun getAccount(): Account
    fun getMarkPrice(symbol: Symbol): MarkPrice
    fun setPositionMode(positionMode: PositionMode)
    fun getQuantityPrecision(symbolValue: String): Int
    fun getPricePrecision(symbolValue: String): Int
    fun getMinQty(symbolValue: String): Double
    fun getMinPrice(symbolValue: String): Double
    fun getMinNotionalValue(symbolValue: String): Double
    fun getTickSize(symbolValue: String): Double
    fun getCommissionRate(symbolValue: String): Double
    fun getLeverage(symbolValue: String): Int
    fun setLeverage(symbolValue: String, leverage: Int): Int
    fun getHistoryInfo(order: Order): JsonNode?
    fun getCurrentBalance(timeStamp: Long): Double
    fun getQuotePrecision(symbolValue: String): Int
}
