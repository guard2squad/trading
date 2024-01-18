package com.g2s.trading

interface Exchange {

    fun getAccount(assets: Assets, timestamp: String): Account
    fun getIndicator(symbol: Symbols, interval: String, limit: Int): Indicator
    fun getPosition(symbol: Symbols, timestamp: String): Position?
    fun closePosition(position: Position)
    fun openPosition(order: Order)
    fun setPositionMode(positionMode: PositionMode)

}
