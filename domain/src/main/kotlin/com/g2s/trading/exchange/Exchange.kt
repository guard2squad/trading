package com.g2s.trading.exchange

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.account.Account
import com.g2s.trading.indicator.MarkPrice
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionMode
import com.g2s.trading.symbol.Symbol
import java.math.BigDecimal

interface Exchange {
    fun getAccount(): Account
    fun closePosition(position: Position, orderType: OrderType, price: BigDecimal = BigDecimal.ZERO) : Long
    fun openPosition(position: Position)
    fun setPositionMode(positionMode: PositionMode)
    fun getMarkPrice(symbol: Symbol): MarkPrice
    fun getPosition(symbol: Symbol): Position
    fun getQuantityPrecision(symbol: Symbol): Int
    fun getMinQty(symbol: Symbol): Double
    fun getMinNotionalValue(symbol: Symbol): Double
    fun getLeverage(symbol: Symbol): Int
    fun setLeverage(symbol: Symbol, leverage: Int): Int
    fun getOpenHistoryInfo(position: Position): JsonNode?
    fun getCloseHistoryInfo(position: Position, orderId: Long): JsonNode?
    fun getCurrentBalance(timeStamp: Long): Double
    fun getCommissionRate(symbol: Symbol): Double
    fun getPricePrecision(symbol: Symbol): Int
    fun getMinPrice(symbol: Symbol): Double
    fun getTickSize(symbol: Symbol): Double
}
