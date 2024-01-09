package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.g2s.trading.Account
import com.g2s.trading.Exchange
import org.springframework.stereotype.Component


@Component
class ExchangeImpl(
    val binanceClient: UMFuturesClientImpl
): Exchange {

    private final val symbol = "USDT"
    override fun getAccount(): Account {


        val a = (ObjectMapper().readTree(binanceClient.account().futuresAccountBalance(linkedMapOf())) as ArrayNode)
            .first { it["asset"].asText() == "USDT" }
            .let { it["availableBalance"].asInt() }

        return Account(a)
    }

    override fun getIndicators(): List<String> {

        val indicatorMap = linkedMapOf<String, String>()

        // call market().markPrice() to get indicators
        getMarkPriceIndicator("markPrice")
        indicatorMap["markPrice"] = getMarkPriceIndicator("markPrice")

        return indicatorMap.values.toList();
    }

    // TODO(구체적인 이름으로 변경)
    fun getMarkPriceIndicator(indicator : String) : String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        // call market().markPrice() to get indicators
        val jsonNode = ObjectMapper().readTree(binanceClient.market().markPrice(parameters))
        println(indicator + " : " + jsonNode.get(indicator).asText())
        return jsonNode.get(indicator).asText();
    }

    // TODO(구체적인 이름으로 변경)
    fun getTicker24HIndicator(indicator : String) : String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().ticker24H(parameters))
        println(indicator + " : " + jsonNode.get(indicator).asText())
        return jsonNode.get(indicator).asText();
    }

    fun getLatestPrice() : Double {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().tickerSymbol(parameters))

        return jsonNode.get("price").asDouble();
    }

    // TODO: OrderBook 에서 가격과 수량이 결정되는 원리?
    // OrderBook의 최고 매수가
    fun getBestBidPriceOnOrderBook() : String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))

        return jsonNode.get("bidPrice").asText();
    }

    // OrderBook의 최고 매수가에 대응하는 매수량
    fun getBestBidQtyOnOrderBook() : String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))

        return jsonNode.get("bidQty").asText();
    }

    // OrderBook의 최고 매도가
    fun getBestAskPriceOnOrderBook() : String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))

        return jsonNode.get("askPrice").asText();
    }

    // OrderBook의 최고 매도가에 대응하는 매도량
    fun getBestAskQtyOnOrderBook() : String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"

        val jsonNode = ObjectMapper().readTree(binanceClient.market().bookTicker(parameters))

        return jsonNode.get("askQty").asText();
    }

    // TODO(404 에러 발생)
    // openInterest
    // period : "5m","15m","30m","1h","2h","4h","6h","12h","1d"
    fun getOpenInterestHistory() : String {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["period"] = "1d"

        return "openInterestHistory"
//        return ObjectMapper().readTree(binanceClient.market().openInterestStatistics(parameters)).asText()
    }


}