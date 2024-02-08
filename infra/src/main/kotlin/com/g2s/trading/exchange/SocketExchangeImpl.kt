package com.g2s.trading.exchange

import com.binance.connector.futures.client.enums.HttpMethod
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl
import com.binance.connector.futures.client.utils.JSONParser
import com.binance.connector.futures.client.utils.RequestHandler
import com.binance.connector.futures.client.utils.WebSocketCallback
import com.g2s.trading.account.Account
import com.g2s.trading.account.Asset
import com.g2s.trading.account.AssetWallet
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.Order
import com.g2s.trading.order.Symbol
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionMode
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.TreeMap


@Component
class SocketExchangeImpl(
    val binanceClient: UMFuturesClientImpl,
    val binanceWebsocketClientImpl: UMWebsocketClientImpl
) : Exchange {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val scope = CoroutineScope(Dispatchers.Default)
    private val channel = Channel<BinanceStreamData>(Channel.UNLIMITED)
    private val candleStickRepository =
        mutableMapOf<CandleStickKey, TreeMap<Long, CandleStick>>() // Key(TimeStamp)'s type is Long
    private val lastPrices = mutableMapOf<Symbol, Double>()
    private var positions = mutableListOf<Position>()
    private var account: Account = Account(listOf())
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

    override fun getAccount(): Account {
        return account
    }

    override fun getPosition(symbol: Symbol): Position? {
        assert(positions.isNotEmpty())
        val position = positions.find { position ->
            symbol == position.symbol
        }
        return position
    }

    override fun getPositions(symbols: List<Symbol>): List<Position> {
        assert(positions.isNotEmpty())
        val filteredPositions = positions.filter { position ->
            symbols.contains(position.symbol)
        }
        return filteredPositions
    }

    override fun getAllPositions(): List<Position> {
        assert(positions.isNotEmpty())
        return positions
    }

    override fun closePosition(position: Position) {
        TODO("Not yet implemented")
    }

    override fun openPosition(order: Order): Position {
        TODO("Not yet implemented")
    }

    override fun setPositionMode(positionMode: PositionMode) {
        TODO("Not yet implemented")
    }

    override fun getCandleStick(symbol: Symbol, interval: Interval, limit: Int): List<CandleStick> {
        val key = CandleStickKey(symbol, interval)
        val candleSticks = candleStickRepository[key]!!.values.toList()
        assert(candleSticks.size > 1)
        return candleSticks
    }

    override fun getLastPrice(symbol: Symbol): Double {
        assert(lastPrices.isNotEmpty())
        return lastPrices[symbol]!!
    }

    private fun getCallback(): WebSocketCallback {
        val callback = WebSocketCallback { it ->
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

                BinanceStreamEventType.ACCOUNT_UPDATE.value -> {
                    logger.info(it)
                    val jsonBalanceAndPosition = jsonNode.get("a")
                    val jsonBalances = jsonBalanceAndPosition.get("B")
                    val assetWallets = jsonBalances.filter { jsonBalance ->
                        Asset.entries.map { it.name }.contains(jsonBalance.get("a").asText())
                    }.map { node ->
                        AssetWallet(
                            asset = Asset.valueOf(node.get("a").asText()),
                            walletBalance = node.get("b").asDouble(),
                            availableBalance = node.get("b").asDouble()
                        )
                    }
                    val account = Account(assetWallets)
                    val positions = jsonBalanceAndPosition.get("P").filter { jsonPosition ->
                        Symbol.entries.map { it.value }.contains(jsonPosition.get("s").asText())
                    }.map { node ->
                        Position(
                            symbol = Symbol.valueOf(node.get("s").asText()),
                            entryPrice = node.get("ep").asDouble(),
                            positionAmt = node.get("pa").asDouble()
                        )
                    }
                    scope.launch {
                        channel.send(BinanceStreamData.AccountData(account))
                        channel.send(BinanceStreamData.PositionData(positions))
                    }
                }
            }
        }
        return callback
    }

    private fun closeConnection(connectionId: Int) {
        binanceWebsocketClientImpl.closeConnection(connectionId)
    }

    private fun openCandleStickStream(symbol: Symbol, interval: Interval) {
        // return connection ID
        binanceWebsocketClientImpl.klineStream(symbol.value, interval.value, getCallback())
    }

    private fun openMarkPriceStream(symbol: Symbol, speed: BinanceMarkPriceStreamSpeed) {
        // return connection ID
        binanceWebsocketClientImpl.markPriceStream(symbol.value, speed.value, getCallback())
    }

    private fun openUserStream() {
        // return connection ID
        assert(isActive)    // listenKey is Active
        binanceWebsocketClientImpl.listenUserStream(getListenKey(), getCallback())
    }

    private final fun openWebSocketConnection() {
        getListenKey()
        Symbol.entries.forEach { symbol ->
            Interval.entries.forEach { interval ->
                openCandleStickStream(symbol, interval)
            }
            openMarkPriceStream(symbol, BinanceMarkPriceStreamSpeed.ONE)
        }
        openUserStream()

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

            is BinanceStreamData.AccountData -> {
                account = binanceStreamData.account
            }

            is BinanceStreamData.PositionData -> {
                positions = binanceStreamData.positions.toMutableList()
            }
        }
    }

    private data class CandleStickKey(
        val symbol: Symbol,
        val interval: Interval
    )

    private fun getListenKey(): String {
        val requestHandler = RequestHandler(binanceClient.apiKey, binanceClient.secretKey, null)
        val response = requestHandler.sendSignedRequest(
            binanceClient.baseUrl,
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
        val requestHandler = RequestHandler(binanceClient.apiKey, binanceClient.secretKey, null)
        requestHandler.sendSignedRequest(
            binanceClient.baseUrl,
            "/fapi/v1/listenKey",
            linkedMapOf(),
            HttpMethod.PUT,
            false
        )
    }

    private fun deleteListenKey() {
        val requestHandler = RequestHandler(binanceClient.apiKey, binanceClient.secretKey, null)
        requestHandler.sendSignedRequest(
            binanceClient.baseUrl,
            "/fapi/v1/listenKey",
            linkedMapOf(),
            HttpMethod.DELETE,
            false
        )
    }
}
