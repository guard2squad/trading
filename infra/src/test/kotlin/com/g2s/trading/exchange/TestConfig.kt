package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class TestConfig {
    companion object {
        const val apiKey = ""
        const val apiSecret = ""
        const val restUrl = "https://testnet.binancefuture.com"
        const val webSocketUrl = "wss://stream.binancefuture.com"
    }

    @Bean
    fun umFuturesClientImpl(): UMFuturesClientImpl {
        return UMFuturesClientImpl(apiKey, apiSecret, restUrl)
    }

}
