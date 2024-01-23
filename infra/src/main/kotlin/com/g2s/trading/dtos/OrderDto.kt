package com.g2s.trading.dtos

import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.PositionMode
import com.g2s.PositionSide
import java.time.LocalDateTime
import kotlin.reflect.full.memberProperties

data class OrderDto(
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: String,
    val positionMode: PositionMode,
    val positionSide: PositionSide,
    val timeStamp: String? = LocalDateTime.now().toString()
) {
    companion object {
        fun toParams(orderDto: OrderDto): LinkedHashMap<String, Any> {
            val params = LinkedHashMap<String, Any>()
            for (prop in OrderDto::class.memberProperties) {
                // null 값은 무시
                val value = prop.get(orderDto) ?: continue
                params[prop.name] = value.toString()
            }

            return params
        }
    }
}
