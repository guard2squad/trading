package com.g2s.trading.exchange

import com.binance.connector.futures.client.enums.DefaultUrls
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BinanceConfig(
    @Value("\${binance.apiKey}")
    private val apiKey: String,
    @Value("\${binance.apiSecret}")
    private val apiSecret: String
) {

    @Bean
    fun binanceClient(): UMFuturesClientImpl {

        val client = UMFuturesClientImpl(
            apiKey,
            apiSecret,
            DefaultUrls.TESTNET_URL
        )

        return client
    }

    @Bean
    fun binanceWebSocketStreamClient(): UMWebsocketClientImpl {

        val webSocketStreamClient = UMWebsocketClientImpl(DefaultUrls.TESTNET_WSS_URL)

        return webSocketStreamClient
    }
}
