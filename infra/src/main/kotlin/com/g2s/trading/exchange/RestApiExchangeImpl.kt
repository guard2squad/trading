package com.g2s.trading.exchange

import com.binance.connector.futures.client.exceptions.BinanceClientException
import com.binance.connector.futures.client.exceptions.BinanceConnectorException
import com.binance.connector.futures.client.exceptions.BinanceServerException
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.g2s.trading.account.Asset
import com.g2s.trading.account.Account
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.order.OrderFailErrors
import com.g2s.trading.indicator.MarkPrice
import com.g2s.trading.order.Order
import com.g2s.trading.position.PositionMode
import com.g2s.trading.symbol.Symbol
import com.g2s.trading.util.BinanceOrderParameterConverter
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
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
    private var exchangeInfo: JsonNode = om.readTree(binanceClient.market().exchangeInfo())

    /**
     * POST /fapi/v1/order
     *
     * newClientOrderId: A unique id among open orders.
     *
     * Automatically generated if not sent. Can only be string following the rule: ^[\.A-Z\:/a-z0-9_-]{1,36}$
     * 요청할 때 newClientOrderId에 값을 넣으면 응답의 clientOrderId이 동일한 값으로 설정됨
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
    override fun sendOrder(order: Order) {
        try {
            val parameters = BinanceOrderParameterConverter.toNewOrderParam(order)

            val response: String = binanceClient.account().newOrder(parameters)
            logger.debug("POST /fapi/v1/order 주문 api 응답: " + pretty.writeValueAsString(om.readTree(response)))
        } catch (e: BinanceClientException) {
            logger.error(e.errorCode.toString())
            logger.error(e.errMsg)
            when (e.errorCode) {
                -2021 -> {
                    throw OrderFailErrors.RETRYABLE_ERROR.error("선물 주문 실패", e, Level.ERROR, e.errMsg)
                }

                -4131 -> {
                    throw OrderFailErrors.IGNORABLE_ERROR.error("선물 주문 실패", e, Level.ERROR, e.errMsg)
                }

                else -> {
                    throw OrderFailErrors.UNKNOWN_ERROR.error("선물 주문 실패", e, Level.ERROR, e.errMsg)
                }
            }
        } catch (e: BinanceServerException) {
            logger.error(e.message)
            throw OrderFailErrors.UNKNOWN_ERROR.error("선물 주문 실패", e, Level.ERROR, e.message)
        } catch (e: BinanceConnectorException) {
            logger.error(e.message)
            throw OrderFailErrors.UNKNOWN_ERROR.error("선물 주문 실패", e, Level.ERROR, e.message)
        }
    }

    /**
     * DELETE /fapi/v1/order
     *
     * Parameters:
     * Name	                Type	Mandatory(필수 여부)
     * symbol	            STRING	YES
     * orderId	            LONG	NO
     * origClientOrderId	STRING	NO
     * recvWindow	        LONG	NO
     * timestamp	        LONG	YES
     *
     * Either orderId or origClientOrderId must be sent.
     *
     * Response 예시:
     *
     * {
     *     "clientOrderId": "myOrder1",
     *     "cumQty": "0",
     *     "cumQuote": "0",
     *     "executedQty": "0",
     *     "orderId": 283194212,
     *     "origQty": "11",
     *     "origType": "TRAILING_STOP_MARKET",
     *     "price": "0",
     *     "reduceOnly": false,
     *     "side": "BUY",
     *     "positionSide": "SHORT",
     *     "status": "CANCELED",
     *     "stopPrice": "9300",                // please ignore when order type is TRAILING_STOP_MARKET
     *     "closePosition": false,   // if Close-All
     *     "symbol": "BTCUSDT",
     *     "timeInForce": "GTC",
     *     "type": "TRAILING_STOP_MARKET",
     *     "activatePrice": "9020",            // activation price, only return with TRAILING_STOP_MARKET order
     *     "priceRate": "0.3",                 // callback rate, only return with TRAILING_STOP_MARKET order
     *     "updateTime": 1571110484038,
     *     "workingType": "CONTRACT_PRICE",
     *     "priceProtect": false,            // if conditional order trigger is protected
     *     "priceMatch": "NONE",              //price match mode
     *     "selfTradePreventionMode": "NONE", //self trading preventation mode
     *     "goodTillDate": 0                  //order pre-set auot cancel time for TIF GTD order
     * }
     *
     */
    override fun cancelOrder(symbol: Symbol, orderId: String) {
        try {
            val params = linkedMapOf<String, Any>(
                "symbol" to symbol.value,
                "origClientOrderId" to orderId
            )
            val response = binanceClient.account().cancelOrder(params)
            // debug
            val jsonResponse = om.readTree(response)
            logger.debug(pretty.writeValueAsString(jsonResponse))
        } catch (e: BinanceClientException) {
            logger.error(e.errorCode.toString())
            logger.error(e.errMsg)
            throw OrderFailErrors.UNKNOWN_ERROR.error("주문 취소 실패", e, Level.ERROR, e.errMsg)
        } catch (e: BinanceServerException) {
            logger.error(e.message)
            throw OrderFailErrors.UNKNOWN_ERROR.error("주문 취소 실패", e, Level.ERROR, e.message)
        } catch (e: BinanceConnectorException) {
            logger.error(e.message)
            throw OrderFailErrors.UNKNOWN_ERROR.error("주문 취소 실패", e, Level.ERROR, e.message)
        }
    }

    override fun getAccount(): Account {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "timestamp" to System.currentTimeMillis().toString()
        )
        val bodyString = binanceClient.account().accountInformation(parameters)
        val bodyJson = om.readTree(bodyString)

        val balance = (bodyJson["assets"] as ArrayNode).first { it["asset"].textValue() == "USDT" }
            .let { it["walletBalance"].textValue().toDouble() to it["availableBalance"].textValue().toDouble() }

        return Account(balance.first, balance.second)
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

    override fun setPositionMode(positionMode: PositionMode) {
        this.positionMode = positionMode
        if (positionMode == PositionMode.ONE_WAY_MODE) {
            this.positionSide = "BOTH"
        }
    }


    override fun getQuantityPrecision(symbolValue: String): Int {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbolValue }!!.get("quantityPrecision").asInt()
    }

    override fun getPricePrecision(symbolValue: String): Int {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbolValue }!!.get("pricePrecision").asInt()
    }

    // 시장가 주문일 때만 적용
    // 시장가 주문이 아닐 때 filterType : LOT_SIZE
    override fun getMinQty(symbolValue: String): Double {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbolValue }!!.get("filters")
            .find { node -> node.get("filterType").asText() == "MARKET_LOT_SIZE" }!!.get("minQty").asDouble()
    }

    override fun getMinPrice(symbolValue: String): Double {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbolValue }!!.get("filters")
            .find { node -> node.get("filterType").asText() == "PRICE_FILTER" }!!.get("minPrice").asDouble()
    }

    override fun getMinNotionalValue(symbolValue: String): Double {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbolValue }!!.get("filters")
            .find { node -> node.get("filterType").asText() == "MIN_NOTIONAL" }!!.get("notional").asDouble()
    }

    override fun getTickSize(symbolValue: String): Double {
        return exchangeInfo.get("symbols")
            .find { node -> node.get("symbol").asText() == symbolValue }!!.get("filters")
            .find { node -> node.get("filterType").asText() == "PRICE_FILTER" }!!.get("tickSize").asDouble()
    }

    override fun getCommissionRate(symbolValue: String): Double {
        val params = linkedMapOf<String, Any>(
            "symbol" to symbolValue
        )
        val jsonResponse = om.readTree(binanceClient.account().getCommissionRate(params))
        val takerCommissionRate = jsonResponse["takerCommissionRate"].asDouble()

        return takerCommissionRate
    }

    override fun getLeverage(symbolValue: String): Int {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to symbolValue
        )
        val jsonResponse = om.readTree(binanceClient.account().positionInformation(parameters))
        val leverage = jsonResponse.get(0).get("leverage").asInt()

        return leverage
    }

    override fun setLeverage(symbolValue: String, leverage: Int): Int {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to symbolValue,
            "leverage" to leverage
        )
        val jsonResponse = om.readTree(binanceClient.account().changeInitialLeverage(parameters))
        val changedLeverage = jsonResponse.get("leverage").asInt()

        return changedLeverage
    }

    /**
     * 주어진 타임스탬프를 기반으로 Binance에서 특정 자산(USDT)의 잔고를 조회합니다.
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
     * 주문 ID를 기반으로 거래 기록을 조회합니다.
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
     * @param order 조회할 주문
     * @return JSON 배열 형태의 거래 기록 데이터 노드. 거래 기록이 없거나 오류가 발생한 경우 null 반환
     */
    override fun getHistoryInfo(order: Order): JsonNode? {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to order.symbol.value,
            "orderId" to order.orderId
        )

        val jsonResponse = om.readTree(binanceClient.account().accountTradeList(parameters))
        logger.info("HISTORY INFO: " + om.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse))

        if (jsonResponse == null || jsonResponse.isEmpty) {
            logger.warn("Invalid or empty response for orderId: $order.id")
            return null
        }

        return jsonResponse
    }

}
