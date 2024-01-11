package com.g2s.trading

interface Exchange {

    fun getAccount(): Account

    fun getIndicators(): Map<String, String>

    fun getPosition(): Position
}