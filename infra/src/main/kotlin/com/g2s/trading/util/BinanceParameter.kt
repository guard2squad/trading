package com.g2s.trading.util

import com.g2s.trading.position.PositionSide
import com.g2s.trading.position.PositionMode
import com.g2s.trading.order.Order
import java.util.LinkedHashMap

object BinanceParameter {

    fun toBinanceOrderParameter(
        order: Order,
        positionMode: PositionMode,
        positionSide: PositionSide
    ): LinkedHashMap<String, Any> {
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = order.symbol.toString()
        parameters["side"] = order.orderSide.toString()
        parameters["type"] = order.orderType.toString()
        parameters["quantity"] = order.quantity
        parameters["timeStamp"] = order.timestamp
        parameters["positionMode"] = positionMode.toString()
        parameters["positionSide"] = positionSide.toString()
        return parameters
    }
}
