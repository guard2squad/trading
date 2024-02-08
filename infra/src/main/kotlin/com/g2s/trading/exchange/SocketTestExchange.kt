package com.g2s.trading.exchange

import com.binance.connector.futures.client.enums.DefaultUrls
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl
import com.binance.connector.futures.client.utils.WebSocketCallback
import com.g2s.trading.account.Account
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.Symbol
import com.g2s.trading.position.Position
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*

class SocketTestExchange {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val testWebSocketStreamClient = UMWebsocketClientImpl(DefaultUrls.USDM_WS_URL)
    private val channel = Channel<BinanceStreamData>(Channel.UNLIMITED) // TODO(buffer size 조정)
    private val candleStickRepository =
        mutableMapOf<CandleStickKey, TreeMap<Long, CandleStick>>() // Key(TimeStamp)'s type is Long
    private val lastPrices = mutableMapOf<Symbol, Double>()

    // TODO(원하는 stream만 열게)
    fun openWebSocketConnection() {
        val symbols = Symbol.entries
        symbols.forEach {
            openCandleStickStream(it, Interval.ONE_MINUTE)
            openMarkPriceStream(it, BinanceMarkPriceStreamSpeed.ONE)
        }

        CoroutineScope(Dispatchers.IO).launch {
            for (binanceStreamData in channel) {
                processBinanceStreamData(binanceStreamData)
            }
        }
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
        // TODO(connection 열려있는지 확인)
        val key = CandleStickKey(symbol, interval)
        val candleSticks = candleStickRepository[key]!!.values.toList()
        return candleSticks
    }

    fun getLastPrice(symbol: Symbol): Double {
        // TODO(connection 열려있는지 확인)
        return lastPrices[symbol]!!
    }

    fun closeConnection(connectionId: Int) {
        testWebSocketStreamClient.closeConnection(connectionId)
    }

    private fun getCallback(): WebSocketCallback {
        val callback = WebSocketCallback {
            logger.info(it)
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
                    CoroutineScope(Dispatchers.IO).launch {
                        if (interval == null) {
                            throw RuntimeException()
                        }
                        channel.send(BinanceStreamData.CandleStickData(symbol, interval, candleStick))
                    }
                }

                BinanceStreamEventType.MARK_PRICE.value -> {
                    val lastMarkPrice = jsonNode.get("p").asDouble()
                    val symbol = Symbol.valueOf(jsonNode.get("s").asText())
                    CoroutineScope(Dispatchers.IO).launch {
                        channel.send(BinanceStreamData.MarkPriceData(symbol, lastMarkPrice))
                    }
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

    private fun processBinanceStreamData(binanceStreamData: BinanceStreamData) {
        when (binanceStreamData) {
            is BinanceStreamData.CandleStickData -> {
                val key = CandleStickKey(binanceStreamData.symbol,binanceStreamData.interval)
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
    test.openWebSocketConnection()
    Thread.sleep(1000)  // TODO(연결 후 어떻게 기다리게 할 것인지..)
    test.getLastPrice(Symbol.BTCUSDT)
    test.getCandleStick(Symbol.BTCUSDT, Interval.ONE_MINUTE)
}
