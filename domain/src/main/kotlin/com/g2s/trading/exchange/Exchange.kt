package com.g2s.trading.exchange

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.account.Account
import com.g2s.trading.history.CloseCondition
import com.g2s.trading.history.CloseHistory
import com.g2s.trading.history.OpenCondition
import com.g2s.trading.history.OpenHistory
import com.g2s.trading.indicator.MarkPrice
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionMode
import com.g2s.trading.symbol.Symbol

interface Exchange {
    fun getAccount(): Account
    fun closePosition(position: Position)
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
    fun getCloseHistoryInfo(position: Position): JsonNode?
    fun getCurrentBalance(timeStamp: Long): Double
}
