package com.g2s.trading.util

import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionMode
import com.g2s.trading.position.PositionSide
import kotlin.math.absoluteValue

object BinanceOrderParameterConverter {
    fun toBinanceOpenPositionParam(
        position: Position,
        positionMode: PositionMode,
        positionSide: PositionSide
    ): LinkedHashMap<String, Any> {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = position.symbol.value
        parameters["side"] = when (position.orderSide) {
            OrderSide.LONG -> "BUY"
            OrderSide.SHORT -> "SELL"
        }
        parameters["type"] = position.orderType.toString()
        parameters["quantity"] = position.positionAmt.toString()
        parameters["timeStamp"] = System.currentTimeMillis()
        parameters["positionMode"] = positionMode.toString()
        parameters["positionSide"] = positionSide.toString()
        // Limit order에서 추가적으로 필요한 Parameter
        if (position.orderType == OrderType.LIMIT) {
            parameters["timeInForce"] = "GTX"
            parameters["price"] = position.entryPrice
        }
        return parameters
    }

    fun toBinanceClosePositionParam(
        position: Position,
        orderType: OrderType,
        price: Double = 0.0,
        positionMode: PositionMode,
        positionSide: PositionSide
    ): LinkedHashMap<String, Any> {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = position.symbol.value
        parameters["side"] = when (position.orderSide) {
            OrderSide.LONG -> "SELL"
            OrderSide.SHORT -> "BUY"
        }
        parameters["type"] = orderType.toString()
        parameters["quantity"] = position.positionAmt.absoluteValue.toString()
        parameters["timeStamp"] = System.currentTimeMillis()
        parameters["positionMode"] = positionMode.toString()
        parameters["positionSide"] = positionSide.toString()
        // Limit order에서 추가적으로 필요한 Parameter
        if (orderType == OrderType.LIMIT) {
            parameters["timeInForce"] = "GTC"
            parameters["price"] = price
        }
        return parameters
    }
}
