package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.g2s.trading.Account
import com.g2s.trading.Exchange
import org.springframework.stereotype.Component

@Component
class ExchangeImpl(
    val binanceClient: UMFuturesClientImpl
): Exchange {
    override fun getAccount(): Account {


        val a = (ObjectMapper().readTree(binanceClient.account().futuresAccountBalance(linkedMapOf())) as ArrayNode)
            .first { it["asset"].asText() == "USDT" }
            .let { it["availableBalance"].asInt() }

        return Account(a)
    }
}