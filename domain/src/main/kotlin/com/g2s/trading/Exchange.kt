package com.g2s.trading

interface Exchange {

    fun getAccount(symbol: String, timestamp: String): Account

    fun getIndicator(symbol: String, interval: String, limit: Int): Indicator

    fun getPosition(symbol: String, timestamp: String): Position

    fun closePosition(symbol: String, side: String, quantity: String, positionSide: String ,type: String, timestamp: String)

    fun openPosition(symbol: String, side: String, quantity: String, positionSide: String ,type: String, timestamp: String)
}