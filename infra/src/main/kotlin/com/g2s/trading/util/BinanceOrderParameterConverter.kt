package com.g2s.trading.util

import com.g2s.trading.order.NewCloseOrder
import com.g2s.trading.order.NewOpenOrder
import com.g2s.trading.order.NewOrder
import com.g2s.trading.order.OrderSide

object BinanceOrderParameterConverter {

    fun toNewOrderParam(order: NewOrder): LinkedHashMap<String, Any> {
        val parameters = linkedMapOf<String, Any>(
            "symbol" to order.symbol.value,
            "quantity" to order.amount,
            "timeStamp" to System.currentTimeMillis(),
            "positionMode" to "ONE_WAY_MODE"
        )

        when (order) {
            is NewOpenOrder.MarketOrder -> {
                parameters["type"] = "MARKET"
            }

            is NewCloseOrder.NewTakeProfitOrder -> {
                parameters["type"] = "TAKE_PROFIT"
                parameters["stopPrice"] = order.price
                parameters["price"] = order.price
            }

            is NewCloseOrder.NewStopLossOrder -> {
                parameters["type"] = "STOP_LOSS"
                parameters["stopPrice"] = order.price
                parameters["price"] = order.price
            }
        }

        parameters["side"] = when (order.side) {
            OrderSide.LONG -> "BUY"
            OrderSide.SHORT -> "SELL"
        }

        return parameters
    }
}
