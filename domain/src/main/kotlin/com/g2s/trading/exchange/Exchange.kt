package com.g2s.trading.exchange

import com.g2s.trading.indicator.MarkPrice
import com.g2s.trading.account.Account
import com.g2s.trading.symbol.Symbol
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionMode

interface Exchange {
    fun getAccount(): Account
    fun getAccount(timeStamp: Long): Account
    fun closePosition(position: Position)
    fun openPosition(position: Position)
    fun setPositionMode(positionMode: PositionMode)
    fun getMarkPrice(symbol: Symbol): MarkPrice
    fun getPosition(symbol: Symbol): Position
    fun getQuantityPrecision(symbol: Symbol): Int
    fun getMinQty(symbol: Symbol): Double
    fun getMinNotionalValue(symbol: Symbol): Double
    fun getClientIdAtOpen(position: Position): String
    fun getClientIdAtClose(position: Position): String
    fun getPositionOpeningTime(position: Position): Long
    fun getPositionClosingTime(position: Position): Long
}
