package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class TestConfig {
    companion object {
        const val apiKey = "f12657e9b005b9665521c0ea1147397cfa87c46bf512fff7e5f300fe60fe958a"
        const val apiSecret = "9941efa22be190ae018c24b32a8bc641d6b8f763b64a87719b75683dcda0a6a3"
        const val restUrl = "https://testnet.binancefuture.com"
        const val webSocketUrl = "wss://stream.binancefuture.com"
    }

    @Bean
    fun umFuturesClientImpl(): UMFuturesClientImpl {
        return UMFuturesClientImpl(apiKey, apiSecret, restUrl)
    }

}
