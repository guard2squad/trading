package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.g2s.trading.Exchange
import com.g2s.trading.ObjectMapperProvider
import com.g2s.trading.order.Order
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.PositionMode
import com.g2s.PositionSide
import com.g2s.trading.Symbol
import com.g2s.trading.account.Account
import com.g2s.trading.account.AssetWallet
import com.g2s.trading.dtos.OrderDto
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.math.abs


@Component
class ExchangeImpl(
    val binanceClient: UMFuturesClientImpl
) : Exchange {
    private val om = ObjectMapperProvider.get()

    private lateinit var positionMode: PositionMode
    private lateinit var positionSide: PositionSide
    // TODO(HEDGE_MODE 일 때 positionSide -> LONG/SHORT)

    override fun setPositionMode(positionMode: PositionMode) {
        this.positionMode = positionMode
        if (positionMode == PositionMode.ONE_WAY_MODE) {
            this.positionSide = PositionSide.BOTH
        }
    }

    override fun getAccount(): Account {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "timestamp" to System.currentTimeMillis().toString()
        )
        val bodyString = binanceClient.account().accountInformation(parameters)
        val bodyJson = om.readTree(bodyString)

        val assetWallets = (bodyJson.get("asset") as ArrayNode).map {
            om.convertValue(it, AssetWallet::class.java)
        }
        val positions = (bodyJson.get("position") as ArrayNode).map {
            om.convertValue(it, Position::class.java)
        }

        return Account(
            assetWallets = assetWallets,
            positions = positions,
        )
    }

    override fun getPosition(symbol: Symbol): Position? {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to symbol.toString(),
            "timestamp" to System.currentTimeMillis().toString()
        )
        val jsonString = binanceClient.account().positionInformation(parameters)

        val position = om.readValue<List<Position>>(jsonString)[0]

        return if (position.positionAmt != 0.0) position else null
    }

    override fun getPositions(symbol: List<Symbol>): List<Position> {
        TODO("Not yet implemented")
    }

    override fun closePosition(
        position: Position
    ) {
        val params = OrderDto.toParams(
            OrderDto(
                symbol = position.symbol,
                side = if (position.positionAmt > 0) OrderSide.SELL else OrderSide.BUY,
                type = OrderType.MARKET,
                quantity = String.format("%.3f", abs(position.positionAmt)),
                positionMode = this.positionMode,
                positionSide = this.positionSide,
                timeStamp = LocalDateTime.now().toString()
            )
        )
        binanceClient.account().newOrder(params)
    }

    override fun openPosition(order: Order) {
        val params = OrderDto.toParams(
            OrderDto(
                symbol = order.symbol.toString(),
                side = order.orderSide,
                type = OrderType.MARKET,
                quantity = order.quantity,
                positionMode = this.positionMode,
                positionSide = this.positionSide,
                timeStamp = LocalDateTime.now().toString()
            )
        )
        binanceClient.account().newOrder(params)
    }


    /**
     *  Kline/candlestick bars for a symbol. Klines are uniquely identified by their open time.
     *
     *  @param interval 1m 3m 5m 15m 30m 1h 2h 4h 6h 8h 12h 1d 3d 1w 1M
     */
    override fun getCandleStick(
        symbol: Symbol,
        interval: Interval,
        limit: Int
    ): List<CandleStick> {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = symbol
        parameters["interval"] = interval.value
        parameters["limit"] = limit
        val jsonString = binanceClient.market().klines(parameters)
        val candleStickDataList: List<List<String>> = ObjectMapper().readValue(jsonString)

        return candleStickDataList.map { candleStickData ->
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
        }
    }

    override fun getLastPrice(symbol: Symbol): Double {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = symbol
        val jsonNode = om.readTree(binanceClient.market().tickerSymbol(parameters))

        return jsonNode.get("price").asDouble()
    }


//    fun getMarkPrice(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPrice(parameters))
//        return jsonNode.get("markPrice").asText();
//    }
//
//    fun getIndexPrice(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPrice(parameters))
//        return jsonNode.get("indexPrice").asText();
//    }
//
//    fun getEstimatedSettlePrice(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPrice(parameters))
//        return jsonNode.get("estimatedSettlePrice").asText();
//    }
//
//    fun getLastFundingRate(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPrice(parameters))
//        return jsonNode.get("lastFundingRate").asText();
//    }
//
//    fun getNextFundingTime(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPrice(parameters))
//        return jsonNode.get("nextFundingTime").asText();
//    }
//
//    /**
//     *   24시간 변화 지표
//     *   "priceChange": "-94.99999800",
//     *   "priceChangePercent": "-95.960",
//     *   "weightedAvgPrice": "0.29628482",
//     *   "lastPrice": "4.00000200",
//     *   "lastQty": "200.00000000",
//     *   "openPrice": "99.00000000",
//     *   "highPrice": "100.00000000",
//     *   "lowPrice": "0.10000000",
//     *   "volume": "8913.30000000",
//     *   "quoteVolume": "15.30000000",
//     *   "openTime": 1499783499040,
//     *   "closeTime": 1499869899040,
//     *   "firstId": 28385,   // First tradeId
//     *   "lastId": 28460,    // Last tradeId
//     *   "count": 76         // Trade count
//     */
//    fun getTicker24HIndicator(indicator: String): String? {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//
//        val ticker24HIndicators = mutableMapOf<String, String>()
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().ticker24H(parameters))
//
//        jsonNode.fields().forEach { (key, value) ->
//            ticker24HIndicators[key] = value.asText()
//        }
//        return ticker24HIndicators[indicator]
//    }
//
//    // TODO: OrderBook 에서 가격과 수량이 결정되는 원리?
//    // OrderBook의 최고 매수가
//    fun getBestBidPriceOnOrderBook(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))
//
//        return jsonNode.get("bidPrice").asText();
//    }
//
//    // OrderBook의 최고 매수가에 대응하는 매수량
//    fun getBestBidQtyOnOrderBook(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))
//
//        return jsonNode.get("bidQty").asText();
//    }
//
//    // OrderBook의 최고 매도가
//    fun getBestAskPriceOnOrderBook(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))
//
//        return jsonNode.get("askPrice").asText();
//    }
//
//    // OrderBook의 최고 매도가에 대응하는 매도량
//    fun getBestAskQtyOnOrderBook(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))
//
//        return jsonNode.get("askQty").asText();
//    }
//
//
//    // openInterest
//    fun getOpenInterest(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().openInterest(parameters))
//
//        return jsonNode.get("openInterest").asText();
//    }
//
//    // Sum of openInterest
//    // period : "5m","15m","30m","1h","2h","4h","6h","12h","1d"
//    fun getOpenInterestHistory(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//        parameters["period"] = "5m"
//
//        return binanceClient.market().openInterestStatistics(parameters)
//    }
//
//    // latest longshortPositionRatio(큰 손들의 움직임 알 수 있음)
//    // period : "5m","15m","30m","1h","2h","4h","6h","12h","1d"
//    fun getLongShortPositionRatio(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//        parameters["period"] = "5m"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().topTraderLongShortPos(parameters))
//
//        return jsonNode.get("longShortRatio").asText();
//    }
//
//    // latest Top Trader Long/Short Ratio (Accounts) (큰 손들의 움직임 알 수 있음)
//    // period : "5m","15m","30m","1h","2h","4h","6h","12h","1d"
//    fun getLongShortPositionAccountRatio(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//        parameters["period"] = "5m"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().topTraderLongShortAccs(parameters))
//
//        return jsonNode.get("longShortRatio").asText();
//    }
//
//
//    // latest Global Long/Short 비율
//    // period : "5m","15m","30m","1h","2h","4h","6h","12h","1d"
//    fun getGlobalLongShortAccountRatio(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//        parameters["period"] = "5m"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().longShortRatio(parameters))
//
//        return jsonNode.get("longShortRatio").asText();
//    }
//
//    // Recent Taker Buy Volume: the total volume of buy orders filled by takers within the period.
//    fun getTakerBuyVol(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//        parameters["period"] = "5m"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().takerBuySellVol(parameters))
//
//        return jsonNode.get("buyVol").asText();
//    }
//
//    // Recent Taker Sell Volume: the total volume of sell orders filled by takers within the period.
//    fun getTakerSellVol(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//        parameters["period"] = "5m"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().takerBuySellVol(parameters))
//
//        return jsonNode.get("sellVol").asText();
//    }
//
//    fun getIndexPriceCandleStickData(): Map<String, String> {
//
//        val candleStickData = mutableMapOf<String, String>()
//
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//        parameters["interval"] = "1h"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().indexPriceKlines(parameters))
//
//        jsonNode.fields().forEach { (key, value) ->
//            candleStickData[key] = value.asText()
//        }
//
//        return candleStickData;
//    }
//
//    fun getMarkPriceCandleStickData(): Map<String, String> {
//
//        val candleStickData = mutableMapOf<String, String>()
//
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//        parameters["interval"] = "1h"
//
//        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPriceKlines(parameters))
//
//        jsonNode.fields().forEach { (key, value) ->
//            candleStickData[key] = value.asText()
//        }
//
//        return candleStickData;
//    }
//
//    // basis : 선물과 현물의 가격 차이
//    // TODO(UMFuturesClientImpl에는 basis 구하는 api 구현 안 되있음 )
//    fun getBasis(): String {
//        val parameters = LinkedHashMap<String, Any>()
//        parameters["symbol"] = "BTCUSDT"
//        parameters["contractType"] = "PERPETUAL"
//        parameters["period"] = "1h"
//
////        val jsonNode = ObjectMapper().readTree(binanceClient.market().some(parameters))
//
////        return jsonNode.get("basis").asText();
//        return "Not Implemented"
//    }

}
