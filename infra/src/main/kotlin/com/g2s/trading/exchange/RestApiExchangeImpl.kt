package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.g2s.trading.MarkPrice
import com.g2s.trading.account.Account
import com.g2s.trading.account.Asset
import com.g2s.trading.account.AssetWallet
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.order.Symbol
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionMode
import com.g2s.trading.position.PositionSide
import com.g2s.trading.util.BinanceOrderParameterConverter
import org.springframework.stereotype.Component


@Component
class RestApiExchangeImpl(
    val binanceClient: UMFuturesClientImpl
) : Exchange {
    private val om = ObjectMapperProvider.get()

    private var positionMode = PositionMode.ONE_WAY_MODE
    private var positionSide = PositionSide.BOTH

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

        return Account(
            assetWallets = assetWallets,
        )
    }

    override fun closePosition(position: Position) {
        val params = BinanceOrderParameterConverter.toBinanceClosePositionParam(position, positionMode, positionSide)
        sendOrder(params)
    }

    override fun openPosition(position: Position) {
        val params = BinanceOrderParameterConverter.toBinanceOpenPositionParam(position, positionMode, positionSide)
        sendOrder(params)
    }

    override fun getPosition(symbol: Symbol): Position {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to symbol.toString(),
            "timestamp" to System.currentTimeMillis().toString()
        )
        val jsonString = binanceClient.account().positionInformation(parameters)

        val position =  om.readValue<List<Position>>(jsonString).first()

        return position
    }

    private fun sendOrder(params: LinkedHashMap<String, Any>) {
        binanceClient.account().newOrder(params)
    }

    override fun getMarkPrice(symbol: Symbol): MarkPrice {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = symbol
        val jsonNode = om.readTree(binanceClient.market().tickerSymbol(parameters))

        return MarkPrice(
            symbol = symbol,
            price = jsonNode.get("price").asDouble()
        )
    }
}
