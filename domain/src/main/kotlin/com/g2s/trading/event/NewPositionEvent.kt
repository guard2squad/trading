package com.g2s.trading.event

import com.g2s.trading.position.NewPosition

sealed class NewPositionEvent(
    source: Any
) : NewEvent(source) {

    data class PositionOpenedEvent(
        val source: NewPosition
    ) : NewPositionEvent(source)

    data class PositionClosedEvent(
        val source: NewPosition
    ) : NewPositionEvent(source)
}