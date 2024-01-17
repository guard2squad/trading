package com.g2s.trading

interface Exchange {

    fun getAccount(symbol: String, timestamp: String): Account

    fun getIndicator(symbol: String, interval: String, limit: Int): Indicator

    fun getPosition(symbol: String, timestamp: String): Position?

    fun closePosition(position: Position)

    fun openPosition(order: Order)
}