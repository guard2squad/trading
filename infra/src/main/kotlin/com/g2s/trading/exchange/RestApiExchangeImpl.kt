package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.binance.connector.futures.client.exceptions.BinanceClientException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.g2s.trading.indicator.MarkPrice
import com.g2s.trading.account.Account
import com.g2s.trading.account.Asset
import com.g2s.trading.account.AssetWallet
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.exceptions.OrderFailException
import com.g2s.trading.history.CloseCondition
import com.g2s.trading.history.History
import com.g2s.trading.history.OpenCondition
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionMode
import com.g2s.trading.position.PositionSide
import com.g2s.trading.symbol.Symbol
import com.g2s.trading.util.BinanceOrderParameterConverter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap


@Component
class RestApiExchangeImpl(
    private val binanceClient: UMFuturesClientImpl
) : Exchange {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val om = ObjectMapperProvider.get()

    private var positionMode = PositionMode.ONE_WAY_MODE
    private var positionSide = PositionSide.BOTH

    // TODO(exchange Info 주기적으로 UPDATE)
    // Exchange MetaData
    private var exchangeInfo: JsonNode = om.readTree(binanceClient.market().exchangeInfo())
    private val openPositionOrderIdMap = ConcurrentHashMap<Position.PositionKey, Long>()
    private val closedPositionOrderIdMap = ConcurrentHashMap<Position.PositionKey, Long>()

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

        val assetWallets = (bodyJson.get("assets") as ArrayNode)
            .filter { jsonNode ->
                Asset.entries.map { it.name }.contains(jsonNode.get("asset").textValue())
            }.map {
                om.convertValue(it, AssetWallet::class.java)
            }

        return Account(assetWallets)
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
    override fun closePosition(position: Position) {
        val params = BinanceOrderParameterConverter.toBinanceClosePositionParam(position, positionMode, positionSide)
        try {
            val orderResult = sendOrder(params)
            val orderId = om.readTree(orderResult).get("orderId").asLong()

            closedPositionOrderIdMap.compute(position.positionKey) { _, _ -> orderId }
            logger.debug(
                "orderId updated: {}, positionKey: {}",
                closedPositionOrderIdMap[position.positionKey],
                position.positionKey
            )
        } catch (e: OrderFailException) {
            throw e
        }
    }

    override fun openPosition(position: Position) {
        val params = BinanceOrderParameterConverter.toBinanceOpenPositionParam(position, positionMode, positionSide)
        try {
            val orderResult = sendOrder(params)
            val orderId = om.readTree(orderResult).get("orderId").asLong()
            logger.debug("orderId: $orderId")

            openPositionOrderIdMap.compute(position.positionKey) { _, _ -> orderId }
            logger.debug(
                "orderId updated: {}, positionKey: {}",
                openPositionOrderIdMap[position.positionKey],
                position.positionKey
            )
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
            logger.debug(response)
            return response
        } catch (e: BinanceClientException) {
            logger.warn("$params\n" + e.errMsg)
            throw OrderFailException("선물 주문 실패")
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

    override fun getOpenHistory(position: Position, condition: OpenCondition): History.Open {
        val openHistoryInfo = getHistoryInfo(position, true)
        if (openHistoryInfo != null) {
            val openHistory = createOpenHistory(
                position,
                condition,
                openHistoryInfo.get("time").asLong(),
                openHistoryInfo.get("commission").asDouble(),
                getCurrentBalance(openHistoryInfo.get("time").asLong())
            )

            return openHistory
        }
        return createOpenHistory(position, condition, 0, 0.0, 0.0)
    }

    fun createOpenHistory(
        position: Position, condition: OpenCondition, transactionTime: Long,
        commission: Double,
        afterBalance: Double
    ) = History.Open(
        historyKey = History.generateHistoryKey(position),
        position = position,
        strategyKey = position.strategyKey,
        openCondition = condition,
        orderSide = position.orderSide,
        orderType = position.orderType,
        transactionTime, commission, afterBalance
    )

    override fun getCloseHistory(position: Position, condition: CloseCondition): History.Close {
        val closeHistoryInfo = getHistoryInfo(position, false)
        if (closeHistoryInfo != null) {

            val closeHistory = createCloseHistory(
                position,
                condition,
                closeHistoryInfo.get("time").asLong(),
                closeHistoryInfo.get("realizedPnl").asDouble(),
                closeHistoryInfo.get("commission").asDouble(),
                getCurrentBalance(closeHistoryInfo.get("time").asLong())
            )

            return closeHistory
        }

        return createCloseHistory(position, condition, 0, 0.0, 0.0, 0.0)
    }

    private fun createCloseHistory(
        position: Position,
        condition: CloseCondition,
        transactionTime: Long,
        realizedPnL: Double,
        commission: Double,
        afterBalance: Double
    ) = History.Close(
        historyKey = History.generateHistoryKey(position),
        position = position,
        strategyKey = position.strategyKey,
        closeCondition = condition,
        orderSide = position.orderSide,
        orderType = position.orderType,
        transactionTime, realizedPnL, commission, afterBalance
    )

    private fun getHistoryInfo(position: Position, isOpen: Boolean): JsonNode? {
        val orderId: Long? = if (isOpen) {
            openPositionOrderIdMap.remove(position.positionKey)
        } else {
            closedPositionOrderIdMap.remove(position.positionKey)
        }

        if (orderId == null) {
            logger.warn("No order ID found for positionKey: {},openTime: {}", position.positionKey, position.openTime)
            return null
        }

        logger.debug("getHistoryInfo-positionKey: {}", position.positionKey)
        logger.debug("getHistoryInfo-orderId: $orderId")

        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to position.symbol.value,
            "orderId" to orderId
        )

        val jsonResponse = om.readTree(binanceClient.account().accountTradeList(parameters))
        logger.debug("API response: {}", jsonResponse.asText())

        if (jsonResponse == null || jsonResponse.isEmpty || jsonResponse.size() != 1) {
            logger.warn("Invalid or empty response for orderId: $orderId")
            logger.warn("positionKey: {},openTime: {},", position.positionKey, position.openTime)
            return null
        }

        return jsonResponse.get(0)
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
    private fun getCurrentBalance(timeStamp: Long): Double {
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
}
