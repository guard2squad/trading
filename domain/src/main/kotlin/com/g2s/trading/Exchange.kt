package com.g2s.trading

interface Exchange {
    fun getAccount(): Account

    fun getIndicators(): List<String>
}