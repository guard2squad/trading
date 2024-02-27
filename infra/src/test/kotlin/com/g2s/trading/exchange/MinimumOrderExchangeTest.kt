package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.order.Symbol
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MinimumOrderExchangeTest {
    private lateinit var binanceClient: UMFuturesClientImpl
    private val om = ObjectMapperProvider.get()
    private val pretty = om.writerWithDefaultPrettyPrinter()


    @BeforeEach
    fun setUp() {
        val restUrl = Urls.TESTNET_URL.value
        binanceClient = UMFuturesClientImpl(restUrl)
    }

    @Test
    fun testExchangeInfo() {
        val jsonExchangeInfo = om.readTree(binanceClient.market().exchangeInfo())
        println(pretty.writeValueAsString(jsonExchangeInfo))
    }

    @Test
    fun testGetMinQtyByMarketOrder() {
        val symbols = Symbol.entries
        for (symbol in symbols) {
            getMinQtyByMarketOrder(symbol)
        }
    }


    private fun getMinQtyByMarketOrder(symbol: Symbol) {
        val jsonSymbols = om.readTree(binanceClient.market().exchangeInfo()).get("symbols")
        if (jsonSymbols.isArray) {
            jsonSymbols.forEach { symbolNode ->
                if (symbolNode.get("symbol").asText() == symbol.value) {
                    val filterNode = symbolNode.get(
                        "filters"
                    )
                    filterNode.forEach { node ->
                        if (node.get("filterType").asText() == "MARKET_LOT_SIZE") {
                            val minQty = node.get("minQty").asText()
                            print("${symbol.value} : ")
                            println(minQty)
                        }
                    }
                }
            }
        }
    }
}
