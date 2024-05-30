package com.g2s.trading.exchange

import com.binance.connector.futures.client.enums.HttpMethod
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl
import com.binance.connector.futures.client.utils.JSONParser
import com.binance.connector.futures.client.utils.RequestHandler
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.event.EventUseCase
import com.g2s.trading.event.TradingEvent
import com.g2s.trading.indicator.CandleStick
import com.g2s.trading.indicator.Interval
import com.g2s.trading.indicator.MarkPrice
import com.g2s.trading.order.OrderUseCase
import com.g2s.trading.order.OrderResult
import com.g2s.trading.symbol.SymbolUseCase
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
    private val orderUseCase: OrderUseCase,
    private val symbolUseCase: SymbolUseCase
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
                openTime = jsonKlineData.get("t").asLong(),
                open = jsonKlineData.get("o").asDouble(),
                high = jsonKlineData.get("h").asDouble(),
                low = jsonKlineData.get("l").asDouble(),
                close = jsonKlineData.get("c").asDouble(),
                volume = jsonKlineData.get("v").asDouble(),
                numberOfTrades = jsonKlineData.get("n").asInt()
            )

            eventUseCase.publishAsyncEvent(
                TradingEvent.CandleStickEvent(candleStick)
            )
        }

        return connectionId
    }

    private fun openMarkPriceStream(symbol: Symbol, speed: BinanceMarkPriceStreamSpeed): Int {
        val connectionId = binanceWebsocketClientImpl.markPriceStream(symbol.value, speed.value) { event ->
            val eventJson = ObjectMapperProvider.get().readTree(event)
            val lastMarkPrice = eventJson.get("p").asDouble()
            val markPrice = MarkPrice(symbol, lastMarkPrice)
            eventUseCase.publishAsyncEvent(
                TradingEvent.MarkPriceRefreshEvent(markPrice)
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
     *
     * {
     *   "e":"ORDER_TRADE_UPDATE",     // Event Type
     *   "E":1568879465651,            // Event Time
     *   "T":1568879465650,            // Transaction Time
     *   "o":{
     *     "s":"BTCUSDT",              // Symbol
     *     "c":"TEST",                 // Client Order Id
     *       // special client order id:
     *       // starts with "autoclose-": liquidation order
     *       // "adl_autoclose": ADL auto close order
     *       // "settlement_autoclose-": settlement order for delisting or delivery
     *     "S":"SELL",                 // Side
     *     "o":"TRAILING_STOP_MARKET", // Order Type
     *     "f":"GTC",                  // Time in Force
     *     "q":"0.001",                // Original Quantity
     *     "p":"0",                    // Original Price
     *     "ap":"0",                   // Average Price
     *     "sp":"7103.04",             // Stop Price. Please ignore with TRAILING_STOP_MARKET order
     *     "x":"NEW",                  // Execution Type
     *     "X":"NEW",                  // Order Status
     *     "i":8886774,                // Order Id
     *     "l":"0",                    // Order Last Filled Quantity
     *     "z":"0",                    // Order Filled Accumulated Quantity
     *     "L":"0",                    // Last Filled Price
     *     "N":"USDT",                 // Commission Asset, will not push if no commission
     *     "n":"0",                    // Commission, will not push if no commission (이번 메시지의 수수료: l(Order Last Filled Quantity) * L(Last Filled Price) * 수수료 율
     *     "T":1568879465650,          // Order Trade Time
     *     "t":0,                      // Trade Id
     *     "b":"0",                    // Bids Notional
     *     "a":"9.91",                 // Ask Notional
     *     "m":false,                  // Is this trade the maker side?
     *     "R":false,                  // Is this reduce only
     *     "wt":"CONTRACT_PRICE",      // Stop Price Working Type
     *     "ot":"TRAILING_STOP_MARKET",// Original Order Type
     *     "ps":"LONG",                // Position Side
     *     "cp":false,                 // If Close-All, pushed with conditional order
     *     "AP":"7476.89",             // Activation Price, only puhed with TRAILING_STOP_MARKET order
     *     "cr":"5.0",                 // Callback Rate, only puhed with TRAILING_STOP_MARKET order
     *     "pP": false,                // If price protection is turned on
     *     "si": 0,                    // ignore
     *     "ss": 0,                    // ignore
     *     "rp":"0",                   // Realized Profit of the trade
     *     "V":"EXPIRE_TAKER",         // STP mode
     *     "pm":"OPPONENT",            // Price match mode
     *     "gtd":0                     // TIF GTD order auto cancel time
     *   }
     * }
     */
    private fun openUserStream(listenKey: String): Int {
        val connectionId = binanceWebsocketClientImpl.listenUserStream(listenKey) { event ->
            val eventJson = ObjectMapperProvider.get().readTree(event)
            val eventType = BinanceUserStreamEventType.valueOf(eventJson.get("e").textValue())
            when (eventType) {
                BinanceUserStreamEventType.ORDER_TRADE_UPDATE -> {
                    val jsonOrder = eventJson.get("o")
                    logger.debug(
                        ObjectMapperProvider.get().writerWithDefaultPrettyPrinter().writeValueAsString(jsonOrder)
                    )
                    val orderStatus = BinanceUserStreamOrderStatus.valueOf(jsonOrder.get("X").asText())
                    when (orderStatus) {
                        BinanceUserStreamOrderStatus.NEW -> {
                            val orderResult = OrderResult.New(
                                orderId = jsonOrder["c"].asText(),
                            )
                            orderUseCase.handleResult(orderResult)
                        }

                        BinanceUserStreamOrderStatus.PARTIALLY_FILLED -> {
                            val orderResult = OrderResult.FilledOrderResult.PartiallyFilled(
                                orderId = jsonOrder["c"].asText(),
                                symbol = symbolUseCase.getSymbol(jsonOrder["s"].asText())!!,
                                price = jsonOrder["L"].asDouble(),
                                amount = jsonOrder["l"].asDouble(),
                                commission = jsonOrder["n"].asDouble(),
                                realizedPnL = jsonOrder["rp"].asDouble()
                            )
                            logger.debug("OrderId[${orderResult.orderId}] 바이낸스 평균가격(ap): " + jsonOrder["ap"].asDouble() + " 바이낸스 누적수량(z): " + jsonOrder["z"].asDouble())
                            orderUseCase.handleResult(orderResult)
                        }

                        BinanceUserStreamOrderStatus.FILLED -> {
                            val orderResult = OrderResult.FilledOrderResult.Filled(
                                orderId = jsonOrder["c"].asText(),
                                symbol = symbolUseCase.getSymbol(jsonOrder["s"].asText())!!,
                                price = jsonOrder["L"].asDouble(),
                                amount = jsonOrder["l"].asDouble(),
                                commission = jsonOrder["n"].asDouble(),
                                realizedPnL = jsonOrder["rp"].asDouble(),
                                averagePrice = jsonOrder["ap"].asDouble(),
                                accumulatedAmount = jsonOrder["z"].asDouble()
                            )
                            logger.debug("OrderId[${orderResult.orderId}] 바이낸스 평균가격(ap): " + jsonOrder["ap"].asDouble() + " 바이낸스 누적수량(z): " + jsonOrder["z"].asDouble())
                            orderUseCase.handleResult(orderResult)
                        }

                        BinanceUserStreamOrderStatus.CANCELED -> {
                            val orderResult = OrderResult.Canceled(
                                orderId = jsonOrder["c"].asText()
                            )
                            orderUseCase.handleResult(orderResult)
                        }

                        else -> {

                        }

                    }
                }

                BinanceUserStreamEventType.ACCOUNT_UPDATE -> {
                    logger.debug(
                        ObjectMapperProvider.get().writerWithDefaultPrettyPrinter().writeValueAsString(eventJson)
                    )
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
