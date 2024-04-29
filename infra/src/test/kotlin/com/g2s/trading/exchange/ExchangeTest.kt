package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.indicator.CandleStick
import com.g2s.trading.indicator.Interval
import com.g2s.trading.symbol.Symbol
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@ContextConfiguration(classes = [TestConfig::class])
@ExtendWith(SpringExtension::class)
@Disabled
class ExchangeTest {

    @Autowired
    private lateinit var binanceClient: UMFuturesClientImpl

    // ObjectMapper
    private val om = ObjectMapperProvider.get()
    private val pretty = om.writerWithDefaultPrettyPrinter()

    @Test
    fun testExchangeInfo() {
        val jsonExchangeInfo = om.readTree(binanceClient.market().exchangeInfo())
        println(pretty.writeValueAsString(jsonExchangeInfo))
    }

    /**
     * GET /fapi/v1/leverageBracket
     * leverage bracket을 조회하는 API
     * It can be accessed with or without specifying a symbol.
     *
     * @queryParameter symbol Optional. The trading symbol to get leverage bracket information for.
     * @queryParameter recvWindow Optional. The number of milliseconds after timestamp the request is valid for.
     * Defaults to 5000 milliseconds if not specified. signed한 요청에서 유효한 parameter.
     * binanceJavaConnector에서 account 클래스의 메서드는 signed한 요청임.
     * @queryParameter timestamp Required. binanceJavaConnector에서 알아서 기본값으로 현재 요청 시간(Epoch)을 입력함.
     *
     * Response:
     * [
     *     {
     *         "symbol": "ETHUSDT",
     *         "notionalCoef": 1.50,  //user symbol bracket multiplier, only appears when user's symbol bracket is adjusted
     *         "brackets": [
     *             {
     *                 "bracket": 1,   // Notional bracket
     *                 "initialLeverage": 75,  // Max initial leverage for this bracket
     *                 "notionalCap": 10000,  // Cap notional of this bracket
     *                 "notionalFloor": 0,  // Notional threshold of this bracket
     *                 "maintMarginRatio": 0.0065, // Maintenance ratio for this bracket
     *                 "cum":0 // Auxiliary number for quick calculation
     *
     *             },
     *         ]
     *     }
     * ]
     * OR (if symbol sent)
     * {
     *     "symbol": "ETHUSDT",
     *     "notionalCoef": 1.50,
     *     "brackets": [
     *         {
     *             "bracket": 1,
     *             "initialLeverage": 75,
     *             "notionalCap": 10000,
     *             "notionalFloor": 0,
     *             "maintMarginRatio": 0.0065,
     *             "cum":0
     *         },
     *     ]
     * }
     */
    @Test
    fun testGetInitialLeverageBracket() {
        val symbol = Symbol.valueOf("BTCUSDT")
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = symbol.value
        val jsonResponse = om.readTree(binanceClient.account().getLeverageBracket(parameters))
        println(pretty.writeValueAsString(jsonResponse))
    }

    /**
     * GET /fapi/v2/positionRisk
     * 포지션 정보를 조회하는 API
     * 포지션이 없어도 조회할 수 있음(positionAmt 값이 0)
     * 초기 레버리지 정보를 포함해서 포지션의 메타데이터 조회 가능
     *
     * @queryParameter symbol Optional. The trading symbol to get position information for.
     * @queryParameter recvWindow Optional. The number of milliseconds after timestamp the request is valid for.
     * Defaults to 5000 milliseconds if not specified. signed한 요청에서 유효한 parameter.
     * binanceJavaConnector에서 account 클래스의 메서드는 signed한 요청임.
     * @queryParameter timestamp Required. binanceJavaConnector에서 알아서 기본값으로 현재 요청 시간(Epoch)을 입력함.
     *
     * Response:
     *
     * [ {
     *   "symbol" : "SNTUSDT",
     *   "positionAmt" : "0",
     *   "entryPrice" : "0.0",
     *   "breakEvenPrice" : "0.0",
     *   "markPrice" : "0.00000000",
     *   "unRealizedProfit" : "0.00000000",
     *   "liquidationPrice" : "0",
     *   "leverage" : "20",
     *   "maxNotionalValue" : "25000",
     *   "marginType" : "cross",
     *   "isolatedMargin" : "0.00000000",
     *   "isAutoAddMargin" : "false",
     *   "positionSide" : "BOTH",
     *   "notional" : "0",
     *   "isolatedWallet" : "0",
     *   "updateTime" : 0,
     *   "isolated" : false,
     *   "adlQuantile" : 0
     * }, {
     *   "symbol" : "SUSHIUSDT",
     *   "positionAmt" : "0",
     *   "entryPrice" : "0.0",
     *   "breakEvenPrice" : "0.0",
     *   "markPrice" : "0.00000000",
     *   "unRealizedProfit" : "0.00000000",
     *   "liquidationPrice" : "0",
     *   "leverage" : "20",
     *   "maxNotionalValue" : "25000",
     *   "marginType" : "cross",
     *   "isolatedMargin" : "0.00000000",
     *   "isAutoAddMargin" : "false",
     *   "positionSide" : "BOTH",
     *   "notional" : "0",
     *   "isolatedWallet" : "0",
     *   "updateTime" : 0,
     *   "isolated" : false,
     *   "adlQuantile" : 0
     * } ]
     *
     *
     *OR (if symbol sent)
     *[ {
     *   "symbol" : "BTCUSDT",
     *   "positionAmt" : "0.000",
     *   "entryPrice" : "0.0",
     *   "breakEvenPrice" : "0.0",
     *   "markPrice" : "66512.60000000",
     *   "unRealizedProfit" : "0.00000000",
     *   "liquidationPrice" : "0",
     *   "leverage" : "10",
     *   "maxNotionalValue" : "40000000",
     *   "marginType" : "isolated",
     *   "isolatedMargin" : "0.00000000",
     *   "isAutoAddMargin" : "false",
     *   "positionSide" : "BOTH",
     *   "notional" : "0",
     *   "isolatedWallet" : "0",
     *   "updateTime" : 1713176068627,
     *   "isolated" : true,
     *   "adlQuantile" : 0
     * } ]
     *
     */
    @Test
    fun testGetInitialLeverage() {
        val symbol = Symbol.valueOf("BTCUSDT")
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = symbol.value
        val jsonResponse = om.readTree(binanceClient.account().positionInformation(parameters))
        val leverage = jsonResponse.get(0).get("leverage").asInt()
        println("Leverage: $leverage")
        println(pretty.writeValueAsString(jsonResponse))
    }

    /**
     * POST /fapi/v1/leverage
     * 포지션의 초기 레버리지를 변경하는 API
     *
     *
     * @param symbol Required. The trading symbol to get position information for.
     * @param leverage Required. Target initial leverage: int from 1 to 125.
     * @param recvWindow Optional. The number of milliseconds after timestamp the request is valid for.
     * Defaults to 5000 milliseconds if not specified. signed한 요청에서 유효한 parameter
     * binanceJavaConnector에서 account 클래스의 메서드는 signed한 요청임.
     * @param timestamp Required. binanceJavaConnector에서 알아서 기본값으로 현재 요청 시간(Epoch)을 입력함.
     */
    @Test
    fun testSetLeverage() {
        val symbol = Symbol.valueOf("BTCUSDT")
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = symbol.value
        parameters["leverage"] = 3
        val jsonResponse = om.readTree(binanceClient.account().changeInitialLeverage(parameters))
        println(pretty.writeValueAsString(jsonResponse))
        val changedLeverage = jsonResponse.get("leverage").asInt()
        println(changedLeverage)
        testGetInitialLeverage()
    }

    /**
     * symbol과 orderId를 통해 단건 거래 기록 조회하는 API를 테스트
     * orderId는 userStream 또는 최근 다건 기록을 조회하면 얻을 수 있음
     */

    @Test
    fun testGetTradeHistory() {
        val symbol = Symbol.valueOf("BTCUSDT")
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = symbol.value
        parameters["orderId"] = 4025653842
        val jsonResponse = om.readTree(binanceClient.account().accountTradeList(parameters))
        jsonResponse[0]
        println(pretty.writeValueAsString(jsonResponse))
    }

    /**
     * symbol을 통해 계정의 최근 다건 거래 기록 조회하는 API를 테스트
     * 디폴트 배열 사이즈: 500
     */
    @Test
    fun testGetTradeHistories() {
        val parameters = linkedMapOf<String, Any>(
            "symbol" to "BTCUSDT",
        )
        val jsonResponse = om.readTree(binanceClient.account().accountTradeList(parameters))
        println(pretty.writeValueAsString(jsonResponse))
    }

    @Test
    fun getAccountTest() {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "timestamp" to 1713204973619
        )
        val bodyString = binanceClient.account().accountInformation(parameters)
        val bodyJson = om.readTree(bodyString)
    }

    // Binance App Trading View 차트에서 LastPrice
    @Test
    fun testGetContinuousContractCandleStickData() {
        val parameters = LinkedHashMap<String, Any>()
        parameters["pair"] = "BTCUSDT"
        parameters["contractType"] = "PERPETUAL"
        parameters["interval"] = "1m"
        parameters["startTime"] = convertMilliseconds("2024-02-28-0623")
        parameters["limit"] = 1
        val jsonCandleStick = om.readTree(binanceClient.market().continuousKlines(parameters))
        println(pretty.writeValueAsString(jsonCandleStick))
    }

    // Binance App Trading View 차트에서 IndexPrice
    @Test
    fun testGetIndexPriceCandleStickData() {
        val parameters = LinkedHashMap<String, Any>()
        parameters["pair"] = "BTCUSDT"
        parameters["interval"] = "1m"
        parameters["startTime"] = convertMilliseconds("2024-02-28-0623")
        parameters["limit"] = 1
        val jsonCandleStick = om.readTree(binanceClient.market().indexPriceKlines(parameters))
        println(pretty.writeValueAsString(jsonCandleStick))
    }

    // Binance App Trading View 차트에서 MarkPrice
    @Test
    fun testGetMarkPriceCandleStickData() {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["interval"] = "1m"
        parameters["startTime"] = convertMilliseconds("2024-02-28-0623")
        parameters["limit"] = 1
        val jsonCandleStick = om.readTree(binanceClient.market().markPriceKlines(parameters))
        println(pretty.writeValueAsString(jsonCandleStick))
    }

    // Binance App Trading View 차트에서 LastPrice
    @Test
    fun testGetCandleStickData() {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["interval"] = "1m"
        parameters["startTime"] = convertMilliseconds("2024-02-28-0623")
        parameters["limit"] = 1
        val jsonCandleStick = om.readTree(binanceClient.market().klines(parameters))
        println(pretty.writeValueAsString(jsonCandleStick))
    }

    @Test
    fun testGetMinQtyByMarketOrder() {
        val symbol = Symbol.valueOf("BTCUSDT")
        getMinQtyByMarketOrder(symbol)
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

    private fun convertMilliseconds(dateString: String): Long {
        // Specify the format of the date string
        val format = SimpleDateFormat("yyyy-MM-dd-HHmm")

        // Set the SimpleDateFormat timezone to UTC
        format.timeZone = TimeZone.getTimeZone("UTC")

        // Parse the date string into a Date object
        val date = format.parse(dateString)

        // Convert the Date object to milliseconds
        val dateInMilliseconds = date.time

        return dateInMilliseconds
    }

    private fun getCandleStickData(symbol: Symbol, interval: Interval, startTime: String): CandleStick {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = symbol.value
        parameters["interval"] = interval.value
        parameters["startTime"] = convertMilliseconds(startTime)
        parameters["limit"] = 1
        val jsonCandleStick = om.readTree(binanceClient.market().klines(parameters)).get(0)
        // json to candlestikc mapping
        val candleStick =
            CandleStick(
                symbol = symbol,
                interval = interval,
                key = jsonCandleStick.get(0).asLong(),
                open = jsonCandleStick.get(1).asDouble(),
                high = jsonCandleStick.get(2).asDouble(),
                low = jsonCandleStick.get(3).asDouble(),
                close = jsonCandleStick.get(4).asDouble(),
                volume = jsonCandleStick.get(5).asDouble(),
                numberOfTrades = jsonCandleStick.get(8).asInt()
            )
        return candleStick
    }

    @Test
    fun getCurrentUtcDateTimeString() {
        val nowSeoul = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
        val formatter = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss")
        println(nowSeoul.format(formatter))
    }

    @Test
    fun getCommissionRate() {
        val params = linkedMapOf<String, Any>(
            "symbol" to "BTCUSDT"
        )
        val res = om.readTree(binanceClient.account().getCommissionRate(params))
        println(pretty.writeValueAsString(res))
    }
}
