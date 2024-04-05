package com.g2s.trading.exchange

import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.position.Position
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class BinanceOrderTracker {

    private val om = ObjectMapperProvider.get()
    private val positionOpenOrderInfoMap = ConcurrentHashMap<Position.PositionKey, OrderInfo>()
    private val positionCloseOrderInfoMap = ConcurrentHashMap<Position.PositionKey, OrderInfo>()

    fun setOpenOrderInfo(position: Position, orderData: String) {
        val jsonNode = om.readTree(orderData)
        positionOpenOrderInfoMap.computeIfAbsent(position.positionKey) { _ ->
            OrderInfo(
                clientOrderId = jsonNode.get("clientOrderId").asText(),
                orderId = jsonNode.get("orderId").asLong(),
                tradeTime = jsonNode.get("updateTime").asLong(),
                orderSide = OrderSide.OPEN
            )
        }
    }

    fun setCloseOrderInfo(position: Position, orderData: String) {
        val jsonNode = om.readTree(orderData)
        positionCloseOrderInfoMap.computeIfAbsent(position.positionKey) { _ ->
            OrderInfo(
                clientOrderId = jsonNode.get("clientOrderId").asText(),
                orderId = jsonNode.get("orderId").asLong(),
                tradeTime = jsonNode.get("updateTime").asLong(),
                orderSide = OrderSide.CLOSE
            )
        }
    }

    fun getOpenOrderInfo(position: Position): OrderInfo {
        return positionOpenOrderInfoMap[position.positionKey]!!
    }

    fun getCloseOrderInfo(position: Position): OrderInfo {
        return positionCloseOrderInfoMap[position.positionKey]!!
    }

    data class OrderInfo(
        val clientOrderId: String,
        val orderId: Long,
        val tradeTime: Long,
        val orderSide: OrderSide
    )

    enum class OrderSide {
        OPEN, CLOSE
    }
}
