package com.g2s.trading.exchange

import com.binance.connector.futures.client.enums.DefaultUrls
import com.binance.connector.futures.client.enums.HttpMethod
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl
import com.binance.connector.futures.client.utils.JSONParser
import com.binance.connector.futures.client.utils.RequestBuilder
import com.binance.connector.futures.client.utils.RequestHandler
import com.binance.connector.futures.client.utils.WebSocketCallback
import com.g2s.trading.account.Account
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.Symbol
import com.g2s.trading.position.Position
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*


@Component
class SocketTestExchange {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val prodClient = UMFuturesClientImpl(
        "jOSzeTGpRm8pCdywFmkrWOM5a9igsWeSJfXSgvLwLzSzpV5dEBBJvC5cBb5UjV60",
        "ghCbhi0B4Fm6Q8XjOuFMIKKrcJxAJKkCUlLviBVQ6xr50Vvl2gM6Fe9ZZINbRs4G",
        DefaultUrls.USDM_PROD_URL
    )
    private val testWebSocketStreamClient = UMWebsocketClientImpl(DefaultUrls.USDM_WS_URL)
    val scope = CoroutineScope(Dispatchers.Default)
    private val channel = Channel<BinanceStreamData>(Channel.UNLIMITED)
    private val candleStickRepository =
        mutableMapOf<CandleStickKey, TreeMap<Long, CandleStick>>() // Key(TimeStamp)'s type is Long
    private val lastPrices = mutableMapOf<Symbol, Double>()
    private var isActive = false

    init {
        openWebSocketConnection()
    }

    @PreDestroy
    fun cleanUp() {
        channel.close()
        scope.cancel()
        deleteListenKey()
    }

    fun getAccount(): Account {
        TODO("Not yet implemented")
    }

    fun getPosition(symbol: Symbol): Position? {
        TODO("Not yet implemented")
    }

    fun getPositions(symbol: List<Symbol>): List<Position> {
        TODO("Not yet implemented")
    }

    fun getAllPositions(): List<Position> {
        TODO("Not yet implemented")
    }

    fun getCandleStick(symbol: Symbol, interval: Interval): List<CandleStick> {
        val key = CandleStickKey(symbol, interval)
        val candleSticks = candleStickRepository[key]!!.values.toList()
        assert(candleSticks.size > 1)
        return candleSticks
    }

    fun getLastPrice(symbol: Symbol): Double? {
        assert(lastPrices.isNotEmpty())
        return lastPrices[symbol]
    }

    fun closeConnection(connectionId: Int) {
        testWebSocketStreamClient.closeConnection(connectionId)
    }

    private fun getCallback(): WebSocketCallback {
        val callback = WebSocketCallback {
//            logger.info(it)
            val jsonNode = ObjectMapperProvider.get().readTree(it)
            val key = jsonNode.get(BinanceStreamAbbreviatedKey.EVENT_TYPE.value).asText()
            when (key) {
                BinanceStreamEventType.KLINE.value -> {
                    val jsonKlineData = jsonNode.get("k")
                    val symbol = Symbol.valueOf(jsonKlineData.get("s").asText())
                    val interval = Interval.fromValue(jsonKlineData.get("i").asText())
                    val candleStick = CandleStick(
                        key = jsonKlineData.get("t").asLong(),
                        open = jsonKlineData.get("o").asDouble(),
                        high = jsonKlineData.get("h").asDouble(),
                        low = jsonKlineData.get("l").asDouble(),
                        close = jsonKlineData.get("c").asDouble(),
                        volume = jsonKlineData.get("v").asDouble(),
                        closeTime = jsonKlineData.get("T").asLong(),
                        quoteAssetVolume = jsonKlineData.get("q").asDouble(),
                        numberOfTrades = jsonKlineData.get("n").asInt(),
                        takerBuyBaseAssetVolume = jsonKlineData.get("V").asDouble(),
                        takerBuyQuoteAssetVolume = jsonKlineData.get("Q").asDouble()
                    )
                    scope.launch {
                        if (interval == null) {
                            throw RuntimeException()
                        }
                        channel.send(BinanceStreamData.CandleStickData(symbol, interval, candleStick))
                    }
                }

                BinanceStreamEventType.MARK_PRICE.value -> {
                    val lastMarkPrice = jsonNode.get("p").asDouble()
                    val symbol = Symbol.valueOf(jsonNode.get("s").asText())
                    scope.launch {
                        channel.send(BinanceStreamData.MarkPriceData(symbol, lastMarkPrice))
                    }
                }

                else -> {
                    logger.info(it)
                }
            }
        }
        return callback
    }

    private fun openCandleStickStream(symbol: Symbol, interval: Interval) {
        // return connection ID
        testWebSocketStreamClient.klineStream(symbol.value, interval.value, getCallback())
    }

    private fun openMarkPriceStream(symbol: Symbol, speed: BinanceMarkPriceStreamSpeed) {
        // return connection ID
        testWebSocketStreamClient.markPriceStream(symbol.value, speed.value, getCallback())
    }

    private fun openAccountStream() {
        listenUserAccountStream(getListenKey(), getCallback())
    }

    private fun openPositionStream() {
        listenUserPositionStream(getListenKey(), getCallback())
    }

    private fun listenUserAccountStream(listenKey: String, onMessageCallback: WebSocketCallback): Int {
        val request = RequestBuilder.buildWebsocketRequest(
            String.format("%s/ws/%s@account", DefaultUrls.USDM_WS_URL, listenKey)
        )
        val noOpCallback = WebSocketCallback {}
        val connectionId = testWebSocketStreamClient.createConnection(
            noOpCallback,
            onMessageCallback,
            noOpCallback,
            noOpCallback,
            request
        )
        return connectionId
    }

    private fun listenUserPositionStream(listenKey: String, onMessageCallback: WebSocketCallback): Int {
        val request = RequestBuilder.buildWebsocketRequest(
            String.format("%s/ws/%s@balance", DefaultUrls.USDM_WS_URL, listenKey)
        )
        val noOpCallback = WebSocketCallback {}
        val connectionId = testWebSocketStreamClient.createConnection(
            noOpCallback,
            onMessageCallback,
            noOpCallback,
            noOpCallback,
            request
        )
        return connectionId
    }


    private final fun openWebSocketConnection() {
        getListenKey()
        openAccountStream()
        openPositionStream()
        val symbols = Symbol.entries
        symbols.forEach {
            openCandleStickStream(it, Interval.ONE_MINUTE)
            openMarkPriceStream(it, BinanceMarkPriceStreamSpeed.ONE)
        }

        scope.launch {
            for (binanceStreamData in channel) {
                processBinanceStreamData(binanceStreamData)
            }
        }

        scope.launch {
            while (isActive) {
                delay(59 * 60 * 1000L)
                keepAliveListenKey()
            }
        }
    }

    private fun getListenKey(): String {
        val requestHandler = RequestHandler(prodClient.apiKey, prodClient.secretKey, null)
        val response = requestHandler.sendSignedRequest(
            DefaultUrls.USDM_PROD_URL,
            "/fapi/v1/listenKey",
            linkedMapOf(),
            HttpMethod.POST,
            false
        )
        val listenKey = JSONParser.getJSONStringValue(response, "listenKey")
        isActive = true
        return listenKey
    }

    private fun keepAliveListenKey() {
        val requestHandler = RequestHandler(prodClient.apiKey, prodClient.secretKey, null)
        requestHandler.sendSignedRequest(
            DefaultUrls.USDM_PROD_URL,
            "/fapi/v1/listenKey",
            linkedMapOf(),
            HttpMethod.PUT,
            false
        )
    }

    private fun deleteListenKey() {
        val requestHandler = RequestHandler(prodClient.apiKey, prodClient.secretKey, null)
        requestHandler.sendSignedRequest(
            DefaultUrls.USDM_PROD_URL,
            "/fapi/v1/listenKey",
            linkedMapOf(),
            HttpMethod.DELETE,
            false
        )
    }

    private fun processBinanceStreamData(binanceStreamData: BinanceStreamData) {
        when (binanceStreamData) {
            is BinanceStreamData.CandleStickData -> {
                val key = CandleStickKey(binanceStreamData.symbol, binanceStreamData.interval)
                val treeMap = candleStickRepository.getOrPut(key) { TreeMap() }
                treeMap[binanceStreamData.candleStick.key] = binanceStreamData.candleStick
            }

            is BinanceStreamData.MarkPriceData -> {
                lastPrices[binanceStreamData.symbol] = binanceStreamData.markPrice
            }
        }
    }

    private data class CandleStickKey(
        val symbol: Symbol,
        val interval: Interval
    )
}

fun main() {
    val test = SocketTestExchange()

//    test.openWebSocketConnection()
//    Thread.sleep(1000)  // TODO(연결 후 어떻게 기다리게 할 것인지..)
//    test.getLastPrice(Symbol.BTCUSDT)
//    test.getCandleStick(Symbol.BTCUSDT, Interval.ONE_MINUTE)
}
