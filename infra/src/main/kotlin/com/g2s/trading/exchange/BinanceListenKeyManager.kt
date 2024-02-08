package com.g2s.trading.exchange

import org.springframework.beans.factory.annotation.Value

class BinanceListenKeyManager(
    @Value("\${binance.apiKey}")
    private val apiKey: String,
    @Value("\${binance.apiSecret}")
    private val apiSecret: String
) {
}
