package com.g2s.trading.dtos

import com.g2s.trading.OrderSide
import com.g2s.trading.OrderType
import com.g2s.trading.PositionMode
import com.g2s.trading.PositionSide
import java.time.LocalDateTime

data class OrderDto(
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: String,
    val positionMode: PositionMode,
    val positionSide: PositionSide = PositionSide.BOTH,
    val timeStamp: String? = LocalDateTime.now().toString()
) {
    // TODO(HEDGE_MODE 일 때 positionSide에서 반드시 BOTH말고 LONG/SHORT 로 하고 싶은데, 어떻게 해야할까?)
}