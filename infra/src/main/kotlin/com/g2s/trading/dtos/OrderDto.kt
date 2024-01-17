package com.g2s.trading.dtos

import com.g2s.trading.OrderSide
import com.g2s.trading.OrderType
import com.g2s.trading.PositionMode
import com.g2s.trading.PositionSide
import java.time.LocalDateTime
import kotlin.reflect.full.memberProperties

data class OrderDto(
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: String,
    val positionMode: PositionMode,
    val positionSide: PositionSide = PositionSide.BOTH,
    val timeStamp: String? = LocalDateTime.now().toString()
) {
    companion object {
        fun toParams(orderDto: OrderDto): LinkedHashMap<String, Any> {
            val params = LinkedHashMap<String, Any>()
            for (prop in OrderDto::class.memberProperties) {
                // null 값은 무시
                val value = prop.get(orderDto) ?: continue
                /// TODO(HEDGE_MODE 일 때 positionSide에서 반드시 BOTH말고 LONG/SHORT 로 하고 싶은데, 어떻게 해야할까?)
                params[prop.name] = value.toString()
            }

            return params
        }
    }
}