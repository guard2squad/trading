package com.g2s.trading.exchange

import com.binance.connector.futures.client.enums.HttpMethod
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl
import com.binance.connector.futures.client.utils.JSONParser
import com.binance.connector.futures.client.utils.RequestHandler
import com.g2s.trading.account.NewAccountUseCase
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.event.EventUseCase
import com.g2s.trading.event.NewTradingEvent
import com.g2s.trading.indicator.CandleStick
import com.g2s.trading.indicator.Interval
import com.g2s.trading.indicator.MarkPrice
import com.g2s.trading.order.NewOrderUseCase
import com.g2s.trading.order.OrderResult
import com.g2s.trading.position.NewPositionUseCase
import com.g2s.trading.symbol.NewSymbolUseCase
import com.g2s.trading.symbol.Symbol
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ExchangeStream(
    private val binanceClient: UMFuturesClientImpl,
    private val binanceWebsocketClientImpl: UMWebsocketClientImpl,
    private val eventUseCase: EventUseCase,
    private val orderUseCase: NewOrderUseCase,
    private val positionUseCase: NewPositionUseCase,
    private val accountUseCase: NewAccountUseCase,
    private val symbolUseCase: NewSymbolUseCase
) {
    private val om = ObjectMapperProvider.get()
    private val pretty = om.writerWithDefaultPrettyPrinter()
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
        // close all connections (A single connection is only valid for 24 hours; expect to be disconnected at the 24hour mark)
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
            val eventJson = ObjectMapperProvider.get().readTree(event)
            val jsonKlineData = eventJson.get("k")
            val intervalValue = Interval.from(jsonKlineData.get("i").asText())
            val candleStick = CandleStick(
                symbol = symbol,
                interval = intervalValue,
                key = jsonKlineData.get("t").asLong(),
                open = jsonKlineData.get("o").asDouble(),
                high = jsonKlineData.get("h").asDouble(),
                low = jsonKlineData.get("l").asDouble(),
                close = jsonKlineData.get("c").asDouble(),
                volume = jsonKlineData.get("v").asDouble(),
                numberOfTrades = jsonKlineData.get("n").asInt()
            )

            eventUseCase.publishEvent(
                NewTradingEvent.CandleStickEvent(candleStick)
            )
        }

        return connectionId
    }

    private fun openMarkPriceStream(symbol: Symbol, speed: BinanceMarkPriceStreamSpeed): Int {
        val connectionId = binanceWebsocketClientImpl.markPriceStream(symbol.value, speed.value) { event ->
            val eventJson = ObjectMapperProvider.get().readTree(event)
            val lastMarkPrice = eventJson.get("p").asDouble()
            val markPrice = MarkPrice(symbol, lastMarkPrice)
            eventUseCase.publishEvent(
                NewTradingEvent.MarkPriceRefreshEvent(markPrice)
            )
        }

        return connectionId
    }

    /**
     * {
     *   "e": "ACCOUNT_UPDATE",                // Event Type
     *   "E": 1564745798939,                   // Event Time
     *   "T": 1564745798938 ,                  // Transaction
     *   "a":                                  // Update Data
     *     {
     *       "m":"ORDER",                      // Event reason type
     *       "B":[                             // Balances
     *         {
     *           "a":"USDT",                   // Asset
     *           "wb":"122624.12345678",       // Wallet Balance
     *           "cw":"100.12345678",          // Cross Wallet Balance
     *           "bc":"50.12345678"            // Balance Change except PnL and Commission
     *         },
     *         {
     *           "a":"BUSD",
     *           "wb":"1.00000000",
     *           "cw":"0.00000000",
     *           "bc":"-49.12345678"
     *         }
     *       ],
     *       "P":[
     *         {
     *           "s":"BTCUSDT",            // Symbol
     *           "pa":"0",                 // Position Amount
     *           "ep":"0.00000",           // Entry Price
     *           "bep":"0",                // breakeven price
     *           "cr":"200",               // (Pre-fee) Accumulated Realized
     *           "up":"0",                 // Unrealized PnL
     *           "mt":"isolated",          // Margin Type
     *           "iw":"0.00000000",        // Isolated Wallet (if isolated position)
     *           "ps":"BOTH"               // Position Side
     *         }，
     *         {
     *           "s":"BTCUSDT",
     *           "pa":"20",
     *           "ep":"6563.66500",
     *           "bep":"6563.6",
     *           "cr":"0",
     *           "up":"2850.21200",
     *           "mt":"isolated",
     *           "iw":"13200.70726908",
     *           "ps":"LONG"
     *         },
     *         {
     *           "s":"BTCUSDT",
     *           "pa":"-10",
     *           "ep":"6563.86000",
     *           "bep":"6563.6",
     *           "cr":"-45.04000000",
     *           "up":"-1423.15600",
     *           "mt":"isolated",
     *           "iw":"6570.42511771",
     *           "ps":"SHORT"
     *         }
     *       ]
     *     }
     * }
     */
    private fun openUserStream(listenKey: String): Int {
        val connectionId = binanceWebsocketClientImpl.listenUserStream(listenKey) { event ->
            val eventJson = ObjectMapperProvider.get().readTree(event)
            val eventType = eventJson.get("e").textValue()
            when (eventType) {
                BinanceUserStreamEventType.ACCOUNT_UPDATE.toString() -> {
                    val jsonBalanceAndPosition = eventJson.get("a")
                    val accountUpdateEventReasonType = jsonBalanceAndPosition.get("m").asText()
                    // TODO: Account Update 할 지 미정
                    val jsonBalances = jsonBalanceAndPosition.get("B")
                    // Position Update
                    val positionRefreshDataList = jsonBalanceAndPosition.get("P").map { node ->
                        val symbolValue = node["s"].asText()
                        val entryPrice = node["ep"].asDouble()
                        val amount = node["pa"].asDouble()
                        val side = node["ps"].asText()
                    // TODO: Position Update 할 지 미정
                    }
                    // Event reason type == ORDER 일 때 포지션 데이터 변동
                    if (accountUpdateEventReasonType == BinanceAccountUpdateEventReasonType.ORDER.name) {
                        // PositionSide가 BOTH인 경우 positionRefreshDataList의 원소는 1개임
                        assert(positionRefreshDataList.size == 1)
                        val positionRefreshData = positionRefreshDataList[0]
                    }
                }

                BinanceUserStreamEventType.ORDER_TRADE_UPDATE.toString() -> {
                    val jsonOrder = eventJson.get("o")
                    val orderStatus = BinanceUserStreamOrderStatus.valueOf(jsonOrder.get("X").asText())
                    when (orderStatus) {
                        BinanceUserStreamOrderStatus.NEW -> {
                            // DO NOTHING
                        }
                        BinanceUserStreamOrderStatus.FILLED -> {
                            val orderResult = OrderResult(
                                orderId = jsonOrder["c"].asText(),
                                symbol = symbolUseCase.getSymbol(jsonOrder["s"].asText())!!,
                                price = jsonOrder["ap"].asDouble(),
                                amount = jsonOrder["z"].asDouble(),
                            )
                            orderUseCase.handleResult(orderResult)
                        }

                        BinanceUserStreamOrderStatus.CANCELED -> {
                            // DO NOTHING
                        }

                        BinanceUserStreamOrderStatus.PARTIALLY_FILLED -> {
                            // DO NOTHING
                        }

                        BinanceUserStreamOrderStatus.EXPIRED -> {
                            // DO NOTHING
                        }

                        BinanceUserStreamOrderStatus.EXPIRED_IN_MATCH -> {
                            // DO NOTHING
                        }
                    }

                }
            }
        }

        return connectionId
    }

    private fun openMarketStreams(): List<Int> {
        val connectionIds = mutableListOf<Int>()
        symbolUseCase.getAllSymbols().forEach { symbol ->
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
