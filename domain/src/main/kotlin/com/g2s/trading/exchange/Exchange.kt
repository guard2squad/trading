package com.g2s.trading.exchange

import com.g2s.trading.account.Account
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.Order
import com.g2s.trading.order.Symbol
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionMode

interface Exchange {
    fun getAccount(): Account
    fun getPosition(symbol: Symbol): Position?
    fun getPositions(symbols: List<Symbol>): List<Position>
    fun getAllPositions(): List<Position>
    fun closePosition(position: Position)
    fun openPosition(order: Order): Position
    fun setPositionMode(positionMode: PositionMode)
    fun getCandleStick(symbol: Symbol, interval: Interval, limit: Int): List<CandleStick>
    fun getLastPrice(symbol: Symbol): Double
}
