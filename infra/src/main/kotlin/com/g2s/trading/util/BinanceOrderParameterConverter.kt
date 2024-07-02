package com.g2s.trading.util

import com.g2s.trading.order.CloseOrder
import com.g2s.trading.order.OpenOrder
import com.g2s.trading.order.Order
import com.g2s.trading.order.OrderSide

object BinanceOrderParameterConverter {

    fun toNewOrderParam(order: Order): LinkedHashMap<String, Any> {
        val parameters = linkedMapOf<String, Any>(
            "newClientOrderId" to order.orderId,
            "symbol" to order.symbol.value,
            "timeStamp" to System.currentTimeMillis(),
            "positionMode" to "ONE_WAY_MODE"
        )

        when (order) {
            is OpenOrder.MarketOrder -> {
                parameters["quantity"] = order.quantity
                parameters["type"] = "MARKET"
                parameters["side"] = orderSide(order.side)
            }
            is CloseOrder.TakeProfitOrder -> {
                addCloseOrderParameters(parameters, order, "TAKE_PROFIT")
            }
            is CloseOrder.StopLossOrder -> {
                addCloseOrderParameters(parameters, order, "STOP")  // STOP_LOSS 아니고, STOP임
            }
            is CloseOrder.MarketOrder -> {
                parameters["quantity"] = order.quantity
                parameters["type"] = "MARKET"
                parameters["side"] = orderSide(order.side)
            }
            is Order.CancelOrder -> {
                throw IllegalArgumentException("CancelOrder cannot be converted to order parameters.")
            }
        }

        return parameters
    }

    private fun orderSide(orderSide: OrderSide): String {
        return when (orderSide) {
            OrderSide.LONG -> "BUY"
            OrderSide.SHORT -> "SELL"
        }
    }

    private fun addCloseOrderParameters(parameters: LinkedHashMap<String, Any>, order: CloseOrder, type: String) {
        parameters["quantity"] = order.quantity
        parameters["type"] = type
        parameters["stopPrice"] = order.price
        parameters["price"] = order.price
        parameters["side"] = orderSide(order.side)
    }
}
