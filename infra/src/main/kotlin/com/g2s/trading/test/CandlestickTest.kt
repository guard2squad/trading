package com.g2s.trading.test

import com.binance.connector.futures.client.enums.DefaultUrls
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.Symbol

class CandlestickTest {

    private val prodClient = UMFuturesClientImpl(
        "",
        "",
        DefaultUrls.USDM_PROD_URL
    )

    fun test() {
        val start = System.currentTimeMillis()
        val duration = 120000
        while (System.currentTimeMillis() - start < duration) {
            val candleSticks = getCandleStick(Symbol.BTCUSDT, Interval.ONE_MINUTE, 2)
            val lastCandleStick = candleSticks.first()
            println(lastCandleStick)
        }
    }

    private fun getCandleStick(
        symbol: Symbol,
        interval: Interval,
        limit: Int
    ): List<CandleStick> {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = symbol.name
        parameters["interval"] = interval.value
        parameters["limit"] = limit
        val jsonString = prodClient.market().klines(parameters)
        val candleStickDataList: List<List<String>> = ObjectMapper().readValue(jsonString)

        return candleStickDataList.map { candleStickData ->
            CandleStick(
                symbol = symbol,
                interval = interval,
                key = candleStickData[0].toLong(),
                open = candleStickData[1].toDouble(),
                high = candleStickData[2].toDouble(),
                low = candleStickData[3].toDouble(),
                close = candleStickData[4].toDouble(),
                volume = candleStickData[5].toDouble(),
                numberOfTrades = candleStickData[8].toInt()
            )
        }
    }
}
//fun main() {
//    val tester = CandlestickTest()
//    tester.test()
//}
