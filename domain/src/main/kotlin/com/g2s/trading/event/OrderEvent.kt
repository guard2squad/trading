package com.g2s.trading.event

import com.g2s.trading.order.CloseOrder

sealed class OrderEvent(
    source: Any
) : Event(source) {

    data class OrderImmediatelyTriggerEvent(
        val source: CloseOrder
    ) : PositionEvent(source)
}