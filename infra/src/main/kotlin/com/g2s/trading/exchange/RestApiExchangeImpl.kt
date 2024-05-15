package com.g2s.trading.exchange

import com.binance.connector.futures.client.exceptions.BinanceClientException
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.g2s.trading.account.Asset
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.exceptions.OrderFailException
import com.g2s.trading.indicator.MarkPrice
import com.g2s.trading.position.PositionMode
import com.g2s.trading.symbol.Symbol
import com.g2s.trading.util.BinanceOrderParameterConverter
import com.g2s.trading.order.NewOrder
import com.g2s.trading.account.NewAccount
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


@Component
class RestApiExchangeImpl(
    private val binanceClient: UMFuturesClientImpl
) : Exchange {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val om = ObjectMapperProvider.get()
    private val pretty = om.writerWithDefaultPrettyPrinter()

    private var positionMode = PositionMode.ONE_WAY_MODE
    private var positionSide = "BOTH"

    // TODO(exchange Info 주기적으로 UPDATE)
    // Exchange MetaData
    private var exchangeInfo: JsonNode = om.readTree(binanceClient.market().exchangeInfo())

    override fun sendOrder(order: NewOrder) {
        TODO("Not yet implemented")
    }


    /***
     *
     *
     * 절취선
     *
     */

    override fun setPositionMode(positionMode: PositionMode) {
        this.positionMode = positionMode
        if (positionMode == PositionMode.ONE_WAY_MODE) {
            this.positionSide = PositionSide.BOTH
        }
    }

    override fun getAccount(): NewAccount {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "timestamp" to System.currentTimeMillis().toString()
        )
        val bodyString = binanceClient.account().accountInformation(parameters)
        val bodyJson = om.readTree(bodyString)

        val balance = (bodyJson["assets"] as ArrayNode)
            .first { it["asset"].textValue() == "USDT" }
            .map { it["walletBalance"].textValue().toDouble() to it["availableBalance"].textValue().toDouble() }[0]

        return NewAccount(balance.first, balance.second)
    }

    /**
     * 포지션을 close하고, 생성된 주문의 ID를 positionOrderIdMap에 저장합니다.
     *
     * @param position 종료할 포지션 정보를 담은 객체입니다.
     * @throws OrderFailException 주문 실패 시 예외를 발생시킵니다.
     *
     * 주문 결과(`orderResult`) 예시:
     * ```json
     * {
     *     "clientOrderId": "testOrder",
     *     "cumQty": "0",
     *     "cumQuote": "0",
     *     "executedQty": "0",
     *     "orderId": 22542179,
     *     "avgPrice": "0.00000",
     *     "origQty": "10",
     *     "price": "0",
     *     "reduceOnly": false,
     *     "side": "BUY",
     *     "positionSide": "SHORT",
     *     "status": "NEW",
     *     "stopPrice": "9300",        // please ignore when order type is TRAILING_STOP_MARKET
     *     "closePosition": false,   // if Close-All
     *     "symbol": "BTCUSDT",
     *     "timeInForce": "GTD",
     *     "type": "TRAILING_STOP_MARKET",
     *     "origType": "TRAILING_STOP_MARKET",
     *     "activatePrice": "9020",    // activation price, only return with TRAILING_STOP_MARKET order
     *     "priceRate": "0.3",         // callback rate, only return with TRAILING_STOP_MARKET order
     *     "updateTime": 1566818724722,
     *     "workingType": "CONTRACT_PRICE",
     *     "priceProtect": false,      // if conditional order trigger is protected
     *     "priceMatch": "NONE",              //price match mode
     *     "selfTradePreventionMode": "NONE", //self trading preventation mode
     *     "goodTillDate": 1693207680000      //order pre-set auot cancel time for TIF GTD order
     *     }
     * ```
     */
    override fun closePosition(position: Position, orderType: OrderType, price: Double): Long {
        val params =
            BinanceOrderParameterConverter.toBinanceClosePositionParam(
                position,
                orderType,
                price,
                positionMode,
                positionSide
            )
        try {
            val orderResult = sendOrder(params)
            val orderId = om.readTree(orderResult).get("orderId").asLong()

            return orderId
        } catch (e: OrderFailException) {
            throw e
        }
    }

    override fun openPosition(position: Position): Long {
        val params = BinanceOrderParameterConverter.toBinanceOpenPositionParam(position, positionMode, positionSide)
        try {
            val orderResult = sendOrder(params)
            val orderId = om.readTree(orderResult).get("orderId").asLong()

            return orderId
        } catch (e: OrderFailException) {
            throw e
        }
    }

    override fun getPosition(symbol: Symbol): Position {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to symbol.value,
            "timestamp" to System.currentTimeMillis().toString()
        )
        val jsonString = binanceClient.account().positionInformation(parameters)

        val position = om.readValue<List<Position>>(jsonString).first()

        return position
    }

    /**
     * POST /fapi/v1/order
     *
     * Request 예시:
     * ```json
     * {
     *     "id": "3f7df6e3-2df4-44b9-9919-d2f38f90a99a",
     *     "method": "order.place",
     *     "params": {
     *         "apiKey": "HMOchcfii9ZRZnhjp2XjGXhsOBd6msAhKz9joQaWwZ7arcJTlD2hGPHQj1lGdTjR",
     *         "positionSide": "BOTH",
     *         "price": "43187.00",
     *         "quantity": 0.1,
     *         "side": "BUY",
     *         "symbol": "BTCUSDT",
     *         "timeInForce": "GTC",
     *         "timestamp": 1702555533821,
     *         "type": "LIMIT",
     *         "signature": "0f04368b2d22aafd0ggc8809ea34297eff602272917b5f01267db4efbc1c9422"
     *     }
     * }
     * ```
     *
     * @queryParameter newClientOrderId: A unique id among open orders.
     * Automatically generated if not sent. Can only be string following the rule: ^[\.A-Z\:/a-z0-9_-]{1,36}$
     * 요청할 때 newClientOrderId에 값 넣으면 response의 clientOrderId에 동일한 값으로 설정됨
     *
     * response 예시:
     * ```json
     * {
     *     "clientOrderId": "testOrder",
     *     "cumQty": "0",
     *     "cumQuote": "0",
     *     "executedQty": "0",
     *     "orderId": 22542179,
     *     "avgPrice": "0.00000",
     *     "origQty": "10",
     *     "price": "0",
     *     "reduceOnly": false,
     *     "side": "BUY",
     *     "positionSide": "SHORT",
     *     "status": "NEW",
     *     "stopPrice": "9300",        // please ignore when order type is TRAILING_STOP_MARKET
     *     "closePosition": false,   // if Close-All
     *     "symbol": "BTCUSDT",
     *     "timeInForce": "GTD",
     *     "type": "TRAILING_STOP_MARKET",
     *     "origType": "TRAILING_STOP_MARKET",
     *     "activatePrice": "9020",    // activation price, only return with TRAILING_STOP_MARKET order
     *     "priceRate": "0.3",         // callback rate, only return with TRAILING_STOP_MARKET order
     *     "updateTime": 1566818724722,
     *     "workingType": "CONTRACT_PRICE",
     *     "priceProtect": false,      // if conditional order trigger is protected
     *     "priceMatch": "NONE",              //price match mode
     *     "selfTradePreventionMode": "NONE", //self trading preventation mode
     *     "goodTillDate": 1693207680000      //order pre-set auot cancel time for TIF GTD order
     * }
     * ```
     */
    private fun sendOrder(params: LinkedHashMap<String, Any>): String {
        try {
            val response: String = binanceClient.account().newOrder(params)
            logger.info("POST /fapi/v1/order 주문 api 응답: " + pretty.writeValueAsString(om.readTree(response)))
            return response
        } catch (e: BinanceClientException) {
            logger.warn("$params\n" + e.errMsg)
            throw OrderFailException(params["type"].toString() + " 선물 주문 실패")
        }
    }

    override fun getMarkPrice(symbol: Symbol): MarkPrice {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to symbol.value
        )
        val jsonNode = om.readTree(binanceClient.market().tickerSymbol(parameters))

        return MarkPrice(
            symbol = symbol,
            price = jsonNode.get("price").asDouble()
        )
    }

    override fun getQuantityPrecision(symbol: Symbol): Int {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbol.value }!!.get("quantityPrecision").asInt()
    }

    // 시장가 주문일 때만 적용
    // 시장가 주문이 아닐 때 filterType : LOT_SIZE
    override fun getMinQty(symbol: Symbol): Double {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbol.value }!!.get("filters")
            .find { node -> node.get("filterType").asText() == "MARKET_LOT_SIZE" }!!.get("minQty").asDouble()
    }

    override fun getMinNotionalValue(symbol: Symbol): Double {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbol.value }!!.get("filters")
            .find { node -> node.get("filterType").asText() == "MIN_NOTIONAL" }!!.get("notional").asDouble()
    }

    override fun getLeverage(symbol: Symbol): Int {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to symbol.value
        )
        val jsonResponse = om.readTree(binanceClient.account().positionInformation(parameters))
        val leverage = jsonResponse.get(0).get("leverage").asInt()

        return leverage
    }

    override fun setLeverage(symbol: Symbol, leverage: Int): Int {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to symbol.value,
            "leverage" to leverage
        )
        val jsonResponse = om.readTree(binanceClient.account().changeInitialLeverage(parameters))
        val changedLeverage = jsonResponse.get("leverage").asInt()

        return changedLeverage
    }

    /**
     * 주어진 타임스탬프를 기반으로 Binance에서 특정 자산(USDT)의 잔고를 조회합니다.
     *
     * 이 함수는 Binance 클라이언트를 통해 계정 정보를 요청하고, 반환된 JSON 데이터에서 "assets" 배열을 분석하여
     * "USDT" 자산의 "walletBalance" 값을 추출하여 반환합니다.
     *
     * @param timeStamp 조회할 시점의 타임스탬프입니다. 이 값은 요청 파라미터로 전달됩니다.
     * @return 조회된 USDT 자산의 지갑 잔액을 `Double` 타입으로 반환합니다.
     */
    override fun getCurrentBalance(timeStamp: Long): Double {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "timestamp" to timeStamp.toString()
        )
        val bodyString = binanceClient.account().accountInformation(parameters)
        val bodyJson = om.readTree(bodyString)

        val balance = bodyJson.get("assets").filter { jsonNode ->
            Asset.entries.map { it.name }.contains(jsonNode.get("asset").textValue())
        }[0].get("walletBalance").asDouble()

        return balance
    }

    /**
     * 해당 포지션과 주문 ID를 기반으로 거래 기록을 조회합니다.
     * <p>
     * 이 메서드는 지정된 포지션과 주문 ID에 대한 거래 기록을 Binance API(GET /fapi/v1/userTrades)를 통해 조회하고,
     * 조회된 데이터는 JSON 배열 형태로 반환됩니다. 각 배열 요소는 거래에 대한 상세 정보를 포함하며,
     * 예시 응답은 아래와 같습니다.
     * </p>
     *
     * <b>응답값 예시:</b>
     * <pre>
     * [
     *   {
     *     "symbol": "BTCUSDT",
     *     "id": 284885830,
     *     "orderId": 4025653842,
     *     "side": "BUY",
     *     "price": "62799.70",
     *     "qty": "0.020",
     *     "realizedPnl": "0",
     *     "marginAsset": "USDT",
     *     "quoteQty": "1255.99400",
     *     "commission": "0.50239760",
     *     "commissionAsset": "USDT",
     *     "time": 1714371606787,
     *     "positionSide": "BOTH",
     *     "buyer": true,
     *     "maker": false
     *   },
     *   {
     *     "symbol": "BTCUSDT",
     *     "id": 284885831,
     *     "orderId": 4025653842,
     *     "side": "BUY",
     *     "price": "62799.90",
     *     "qty": "0.231",
     *     "realizedPnl": "0",
     *     "marginAsset": "USDT",
     *     "quoteQty": "14506.77690",
     *     "commission": "5.80271076",
     *     "commissionAsset": "USDT",
     *     "time": 1714371606787,
     *     "positionSide": "BOTH",
     *     "buyer": true,
     *     "maker": false
     *   }
     * ]
     * </pre>
     *
     * @param position  조회할 포지션 객체
     * @param orderId   조회할 거래의 주문 ID
     * @return JSON 배열 형태의 거래 기록 데이터 노드. 거래 기록이 없거나 오류가 발생한 경우 null 반환
     */
    override fun getHistoryInfo(position: Position, orderId: Long): JsonNode? {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to position.symbol.value,
            "orderId" to orderId
        )

        val jsonResponse = om.readTree(binanceClient.account().accountTradeList(parameters))
        logger.info("HISTORY INFO: " + om.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse))

        if (jsonResponse == null || jsonResponse.isEmpty) {
            logger.warn("Invalid or empty response for orderId: $orderId")
            logger.warn("positionKey: {},openTime: {},", position.positionKey, position.openTime)
            return null
        }

        return jsonResponse
    }

    override fun getCommissionRate(symbol: Symbol): Double {
        val params = linkedMapOf<String, Any>(
            "symbol" to symbol.value
        )
        val jsonResponse = om.readTree(binanceClient.account().getCommissionRate(params))
        val takerCommissionRate = jsonResponse["takerCommissionRate"].asDouble()

        return takerCommissionRate
    }

    override fun getPricePrecision(symbol: Symbol): Int {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbol.value }!!.get("pricePrecision").asInt()
    }

    override fun getMinPrice(symbol: Symbol): Double {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbol.value }!!.get("filters")
            .find { node -> node.get("filterType").asText() == "PRICE_FILTER" }!!.get("minPrice").asDouble()
    }

    override fun getTickSize(symbol: Symbol): Double {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbol.value }!!.get("filters")
            .find { node -> node.get("filterType").asText() == "PRICE_FILTER" }!!.get("tickSize").asDouble()
    }

    override fun cancelOrder(symbol: Symbol, orderId: Long) {
        val params = linkedMapOf<String, Any>(
            "symbol" to symbol.value,
            "orderId" to orderId
        )
        val response = binanceClient.account().cancelOrder(params)
        val jsonResponse = om.readTree(response)
        logger.debug("주문 취소: " + pretty.writeValueAsString(jsonResponse))
    }
}
