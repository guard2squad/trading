package com.g2s.trading

import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval

interface Exchange {

    fun getAccount(): Account
    fun getPosition(symbol: Symbol): Position?

    fun getPositions(symbol: List<Symbol>): List<Position>
    fun closePosition(position: Position)
    fun openPosition(order: Order)
    fun setPositionMode(positionMode: PositionMode)

    fun getCandleStick(symbol: Symbol, interval: Interval, limit: Int): List<CandleStick>
    fun getLastPrice(symbol: Symbol): Double
}
