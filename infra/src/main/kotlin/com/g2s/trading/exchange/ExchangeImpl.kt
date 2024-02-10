package com.g2s.trading.exchange

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.order.Symbol
import com.g2s.trading.account.Account
import com.g2s.trading.account.Asset
import com.g2s.trading.account.AssetWallet
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionSide
import com.g2s.trading.order.Order
import com.g2s.trading.position.PositionMode
import com.g2s.trading.util.BinanceParameter
import org.springframework.stereotype.Component


@Component
class ExchangeImpl(
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

    override fun getPosition(symbol: Symbol): Position? {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "symbol" to symbol.toString(),
            "timestamp" to System.currentTimeMillis().toString()
        )
        val jsonString = binanceClient.account().positionInformation(parameters)

        val position =  om.readValue<List<Position>>(jsonString).first()

        return if (position.positionAmt != 0.0) position else null
    }

    override fun getPositions(symbols: List<Symbol>): List<Position> {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "timestamp" to System.currentTimeMillis().toString()
        )
        val bodyString = binanceClient.account().accountInformation(parameters)
        val bodyJson = om.readTree(bodyString)

        val positions = (bodyJson.get("positions") as ArrayNode)
            .filter { jsonNode ->
                symbols.map { it.name }.contains(jsonNode.get("symbol").textValue())
            }
            .map {
                om.convertValue(it, Position::class.java)
            }
        return positions
    }

    override fun getAllPositions(): List<Position> {
        val parameters: LinkedHashMap<String, Any> = linkedMapOf(
            "timestamp" to System.currentTimeMillis().toString()
        )
        val bodyString = binanceClient.account().accountInformation(parameters)
        val bodyJson = om.readTree(bodyString)

        val positions = (bodyJson.get("positions") as ArrayNode)
            .filter { jsonNode ->
                Symbol.entries.map { it.name }.contains(jsonNode.get("symbol").textValue())
            }
            .map {
                om.convertValue(it, Position::class.java)
            }
        return positions
    }

    override fun closePosition(position: Position) {
        val params = BinanceParameter.toBinanceClosePositionParam(position, positionMode, positionSide)
        binanceClient.account().newOrder(params)
    }

    override fun openPosition(order: Order): Position {
        val params = BinanceParameter.toBinanceOpenPositionParam(order, positionMode, positionSide)
        binanceClient.account().newOrder(params)
        val position = getPosition(order.symbol)!!
        return position
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
        parameters["symbol"] = symbol.name
        parameters["interval"] = interval.value
        parameters["limit"] = limit
        val jsonString = binanceClient.market().klines(parameters)
        val candleStickDataList: List<List<String>> = ObjectMapper().readValue(jsonString)

        return candleStickDataList.map { candleStickData ->
            CandleStick(
                key = candleStickData[0].toLong(),
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
}
