package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.g2s.trading.*
import org.springframework.stereotype.Component


@Component
class ExchangeImpl(
    val binanceClient: UMFuturesClientImpl,
    private val account: AccountUseCase
) : Exchange {

    private final val symbol = "USDT"

    override fun getAccount(): Account {
        val parameters = LinkedHashMap<String, Any>()

        //  TODO(TimeSynchronization)
        parameters["timestamp"] = System.currentTimeMillis().toString()

        val objectMapper = ObjectMapper()

        val jsonString = binanceClient.account().futuresAccountBalance(parameters)

        val accountList: List<Account> = objectMapper.readValue(jsonString)

        return accountList.find { it.asset == symbol } ?: throw Exception("USDT Account not found")
    }

    fun getAvailableBalance(): Double {
        return getAccount().availableBalance;
    }

    // TODO(Indicator 데이터 클래스 만들기)
    override fun getIndicators(): Map<String, String> {

        val indicatorMap = linkedMapOf<String, String>()

        // CandleStickData
        val candleStick = getCandleStickData()
        indicatorMap["openTime"] = candleStick.openTime.toString()
        indicatorMap["open"] = candleStick.open.toString()
        indicatorMap["high"] = candleStick.high.toString()
        indicatorMap["low"] = candleStick.low.toString()
        indicatorMap["close"] = candleStick.close.toString()
        indicatorMap["volume"] = candleStick.volume.toString()
        indicatorMap["closeTime"] = candleStick.closeTime.toString()
        indicatorMap["quoteAssetVolume"] = candleStick.quoteAssetVolume.toString()
        indicatorMap["numberOfTrades"] = candleStick.numberOfTrades.toString()
        indicatorMap["takerBuyBaseAssetVolume"] = candleStick.takerBuyBaseAssetVolume.toString()
        indicatorMap["takerBuyQuoteAssetVolume"] = candleStick.takerBuyQuoteAssetVolume.toString()

        // current price
        indicatorMap["latestPrice"] = getLatestPrice()

        return indicatorMap
    }

    override fun getPosition(): Position {
        TODO("Not yet implemented")
    }

    fun getPositions(): List<Position> {

        val json = ""

        val objectMapper = ObjectMapper()

        val root: JsonNode = objectMapper.readTree(json)

        val positionNode = root.get("positions")

//        val positions = objectMapper.readValue(positionNode.traverse(), object : TypeReference<List<Position>>() {})

        return TODO("Not yet implemented")
    }

    fun getMarkPrice(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPrice(parameters))
        return jsonNode.get("markPrice").asText();
    }

    fun getIndexPrice(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPrice(parameters))
        return jsonNode.get("indexPrice").asText();
    }

    fun getEstimatedSettlePrice(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPrice(parameters))
        return jsonNode.get("estimatedSettlePrice").asText();
    }

    fun getLastFundingRate(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPrice(parameters))
        return jsonNode.get("lastFundingRate").asText();
    }

    fun getNextFundingTime(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPrice(parameters))
        return jsonNode.get("nextFundingTime").asText();
    }

    /**
     *   24시간 변화 지표
     *   "priceChange": "-94.99999800",
     *   "priceChangePercent": "-95.960",
     *   "weightedAvgPrice": "0.29628482",
     *   "lastPrice": "4.00000200",
     *   "lastQty": "200.00000000",
     *   "openPrice": "99.00000000",
     *   "highPrice": "100.00000000",
     *   "lowPrice": "0.10000000",
     *   "volume": "8913.30000000",
     *   "quoteVolume": "15.30000000",
     *   "openTime": 1499783499040,
     *   "closeTime": 1499869899040,
     *   "firstId": 28385,   // First tradeId
     *   "lastId": 28460,    // Last tradeId
     *   "count": 76         // Trade count
     */
    fun getTicker24HIndicator(indicator: String): String? {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val ticker24HIndicators = mutableMapOf<String, String>()

        val jsonNode = ObjectMapper().readTree(binanceClient.market().ticker24H(parameters))

        jsonNode.fields().forEach { (key, value) ->
            ticker24HIndicators[key] = value.asText()
        }
        return ticker24HIndicators[indicator]
    }


    fun getLatestPrice(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().tickerSymbol(parameters))

        return jsonNode.get("price").asText()
    }

    // TODO: OrderBook 에서 가격과 수량이 결정되는 원리?
    // OrderBook의 최고 매수가
    fun getBestBidPriceOnOrderBook(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))

        return jsonNode.get("bidPrice").asText();
    }

    // OrderBook의 최고 매수가에 대응하는 매수량
    fun getBestBidQtyOnOrderBook(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))

        return jsonNode.get("bidQty").asText();
    }

    // OrderBook의 최고 매도가
    fun getBestAskPriceOnOrderBook(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))

        return jsonNode.get("askPrice").asText();
    }

    // OrderBook의 최고 매도가에 대응하는 매도량
    fun getBestAskQtyOnOrderBook(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))

        return jsonNode.get("askQty").asText();
    }

    // openInterest
    fun getOpenInterest(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().openInterest(parameters))

        return jsonNode.get("openInterest").asText();
    }


    // TODO(Testnet에서 동작하지 않는지 확인 중)
    // TODO(period 넣는 api는 Testnet에서 안 되냐?)
    // Sum of openInterest
    // period : "5m","15m","30m","1h","2h","4h","6h","12h","1d"
    fun getOpenInterestHistory(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["period"] = "5m"

        return binanceClient.market().openInterestStatistics(parameters)
    }

    // latest longshortPositionRatio(큰 손들의 움직임 알 수 있음)
    // period : "5m","15m","30m","1h","2h","4h","6h","12h","1d"
    fun getLongShortPositionRatio(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["period"] = "5m"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().topTraderLongShortPos(parameters))

        return jsonNode.get("longShortRatio").asText();
    }

    // latest Top Trader Long/Short Ratio (Accounts) (큰 손들의 움직임 알 수 있음)
    // period : "5m","15m","30m","1h","2h","4h","6h","12h","1d"
    fun getLongShortPositionAccountRatio(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["period"] = "5m"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().topTraderLongShortAccs(parameters))

        return jsonNode.get("longShortRatio").asText();
    }

    // latest Global Long/Short 비율
    // period : "5m","15m","30m","1h","2h","4h","6h","12h","1d"
    fun getGlobalLongShortAccountRatio(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["period"] = "5m"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().longShortRatio(parameters))

        return jsonNode.get("longShortRatio").asText();
    }


    // Recent Taker Buy Volume: the total volume of buy orders filled by takers within the period.
    fun getTakerBuyVol(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["period"] = "5m"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().takerBuySellVol(parameters))

        return jsonNode.get("buyVol").asText();
    }

    // Recent Taker Sell Volume: the total volume of sell orders filled by takers within the period.
    fun getTakerSellVol(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["period"] = "5m"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().takerBuySellVol(parameters))

        return jsonNode.get("sellVol").asText();
    }

    // Kline/candlestick bars for a symbol. Klines are uniquely identified by their open time.
    // interval : 1m 3m 5m 15m 30m 1h 2h 4h 6h 8h 12h 1d 3d 1w 1M
    fun getCandleStickData(): CandleStick {

        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["interval"] = "1m"
        // limit 증가할 수록 interval만큼 이전 시간의 데이터도 받아온다. (limit = 1이면 현재 시간의 데이터)
        parameters["limit"] = 1

        // 정상적으로 값이 나온다고 가정하고 구현
        val jsonString = binanceClient.market().klines(parameters)

        val candleStickDataList: List<List<String>> = ObjectMapper().readValue(jsonString)

        return candleStickDataList.firstOrNull()?.let { candleStickData ->
            CandleStick(
                openTime = candleStickData[0].toLong(),
                open = candleStickData[1].toDouble(),
                high = candleStickData[2].toDouble(),
                low = candleStickData[3].toDouble(),
                close = candleStickData[4].toDouble(),
                volume = candleStickData[5].toDouble(),
                closeTime = candleStickData[6].toLong(),
                quoteAssetVolume = candleStickData[7].toDouble(),
                numberOfTrades = candleStickData[8].toInt(),
                takerBuyBaseAssetVolume = candleStickData[9].toDouble(),
                takerBuyQuoteAssetVolume = candleStickData[10].toDouble(),
            )
        } ?: throw NoSuchElementException("No candlestick data found")
    }

    fun getIndexPriceCandleStickData(): Map<String, String> {

        val candleStickData = mutableMapOf<String, String>()

        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["interval"] = "1h"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().indexPriceKlines(parameters))

        jsonNode.fields().forEach { (key, value) ->
            candleStickData[key] = value.asText()
        }

        return candleStickData;
    }

    fun getMarkPriceCandleStickData(): Map<String, String> {

        val candleStickData = mutableMapOf<String, String>()

        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["interval"] = "1h"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPriceKlines(parameters))

        jsonNode.fields().forEach { (key, value) ->
            candleStickData[key] = value.asText()
        }

        return candleStickData;
    }

    // basis : 선물과 현물의 가격 차이
    // TODO(UMFuturesClientImpl에는 basis 구하는 api 구현 안 되있음 )
    fun getBasis(): String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["contractType"] = "PERPETUAL"
        parameters["period"] = "1h"

//        val jsonNode = ObjectMapper().readTree(binanceClient.market().some(parameters))

//        return jsonNode.get("basis").asText();
        return "Not Implemented"
    }

}