package com.g2s.trading.exchange

import com.binance.connector.futures.client.enums.HttpMethod
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl
import com.binance.connector.futures.client.utils.JSONParser
import com.binance.connector.futures.client.utils.RequestHandler
import com.g2s.trading.EventUseCase
import com.g2s.trading.MarkPrice
import com.g2s.trading.PositionEvent
import com.g2s.trading.TradingEvent
import com.g2s.trading.UserEvent
import com.g2s.trading.account.Account
import com.g2s.trading.account.Asset
import com.g2s.trading.account.AssetWallet
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.Symbol
import com.g2s.trading.position.PositionRefreshData
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit


@Component
class ExchangeStream(
    private val binanceClient: UMFuturesClientImpl,
    private val binanceWebsocketClientImpl: UMWebsocketClientImpl,
    private val eventUseCase: EventUseCase
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val socketConnectionIds: MutableSet<Int> = mutableSetOf()
    private lateinit var listenKey: String

    init {
        // create or get listen key
        listenKey = getListenKey()

        // keep alive listen key
        keepAliveListenKey()

        // open market stream
        val marketStreamConnectionIds = openMarketStreams()
        socketConnectionIds.addAll(marketStreamConnectionIds)

        // open user stream
        val userStreamConnectionId = openUserStream(listenKey)
        socketConnectionIds.add(userStreamConnectionId)
    }

    @Scheduled(fixedRate = 59, timeUnit = TimeUnit.MINUTES)
    fun scheduleKeepAliveListenKey() {
        keepAliveListenKey()
    }

    @Scheduled(fixedRate = 23, timeUnit = TimeUnit.HOURS)
    fun scheduleRefreshSocket() {
        // close all connections (A single connection is only valid for 24 hours; expect to be disconnected at the 24 hour mark)
        // https://binance-docs.github.io/apidocs/futures/en/#websocket-market-streams
        socketConnectionIds.forEach { closeConnection(it) }

        // open market stream
        val marketStreamConnectionIds = openMarketStreams()
        socketConnectionIds.addAll(marketStreamConnectionIds)

        // open user stream
        val userStreamConnectionId = openUserStream(listenKey)
        socketConnectionIds.add(userStreamConnectionId)
    }

    private fun closeConnection(connectionId: Int) {
        binanceWebsocketClientImpl.closeConnection(connectionId)
    }

    private fun openCandleStickStream(symbol: Symbol, interval: Interval): Int {
        val connectionId = binanceWebsocketClientImpl.klineStream(symbol.value, interval.value) { event ->
//            logger.debug("symbol: $symbol, interval: $interval")
            val eventJson = ObjectMapperProvider.get().readTree(event)
            val jsonKlineData = eventJson.get("k")
            val symbol = Symbol.valueOf(jsonKlineData.get("s").asText())
            val interval = Interval.from(jsonKlineData.get("i").asText())
            val candleStick = CandleStick(
                symbol = symbol,
                interval = interval,
                key = jsonKlineData.get("t").asLong(),
                open = jsonKlineData.get("o").asDouble(),
                high = jsonKlineData.get("h").asDouble(),
                low = jsonKlineData.get("l").asDouble(),
                close = jsonKlineData.get("c").asDouble(),
                volume = jsonKlineData.get("v").asDouble(),
                numberOfTrades = jsonKlineData.get("n").asInt()
            )

            eventUseCase.publishEvent(
                TradingEvent.CandleStickEvent(candleStick)
            )
        }

        return connectionId
    }

    private fun openMarkPriceStream(symbol: Symbol, speed: BinanceMarkPriceStreamSpeed): Int {
        val connectionId = binanceWebsocketClientImpl.markPriceStream(symbol.value, speed.value) { event ->
            val eventJson = ObjectMapperProvider.get().readTree(event)
            val lastMarkPrice = eventJson.get("p").asDouble()
            val symbol = Symbol.valueOf(eventJson.get("s").asText())

            val markPrice = MarkPrice(symbol, lastMarkPrice)
            eventUseCase.publishEvent(
                TradingEvent.MarkPriceRefreshEvent(markPrice)
            )
        }

        return connectionId
    }

    private fun openUserStream(listenKey: String): Int {
        val connectionId = binanceWebsocketClientImpl.listenUserStream(listenKey) { event ->
            val eventJson = ObjectMapperProvider.get().readTree(event)

            val jsonBalanceAndPosition = eventJson.get("a")
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
            val positionRefreshDatas = jsonBalanceAndPosition.get("P").filter { jsonPosition ->
                Symbol.entries.map { it.value }.contains(jsonPosition.get("s").asText())
            }.map { node ->
                PositionRefreshData(
                    symbol = Symbol.valueOf(node.get("s").asText()),
                    entryPrice = node.get("ep").asDouble(),
                    positionAmt = node.get("pa").asDouble()
                )
            }

            eventUseCase.publishEvent(
                UserEvent.AccountRefreshEvent(account),
                PositionEvent.PositionRefreshEvent(positionRefreshDatas)
            )
        }

        return connectionId
    }

    private fun openMarketStreams(): List<Int> {
        val connectionIds = mutableListOf<Int>()
        Symbol.entries.forEach { symbol ->
            Interval.entries.forEach { interval ->
                val candleStickConnectionId = openCandleStickStream(symbol, interval)
                connectionIds.add(candleStickConnectionId)
            }
            val markPriceConnectionId = openMarkPriceStream(symbol, BinanceMarkPriceStreamSpeed.ONE)
            connectionIds.add(markPriceConnectionId)
        }

        return connectionIds
    }

    private fun getListenKey(): String {
        val requestHandler = RequestHandler(binanceClient.apiKey, binanceClient.secretKey, null)
        val response = requestHandler.sendSignedRequest(
            binanceClient.baseUrl,
            "/fapi/v1/listenKey",
            linkedMapOf(),
            HttpMethod.POST,
            false
        )
        return JSONParser.getJSONStringValue(response, "listenKey")
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
}
