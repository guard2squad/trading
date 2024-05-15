package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class TestConfig {
    companion object {
        const val apiKey = "8340e6e126133524b9fe7d140743ce1bdfdc75e658162390f4c4e2bebdb1612c"
        const val apiSecret = "b7345d742b8007d3a5c2d4d336a4b5c71c317c581321864aceb220acee93ec44"
        const val restUrl = "https://testnet.binancefuture.com"
        const val webSocketUrl = "wss://stream.binancefuture.com"
    }

    @Bean
    fun umFuturesClientImpl(): UMFuturesClientImpl {
        return UMFuturesClientImpl(apiKey, apiSecret, restUrl)
    }

}
