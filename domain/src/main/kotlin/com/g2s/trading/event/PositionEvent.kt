package com.g2s.trading.event

import com.g2s.trading.position.Position

sealed class PositionEvent(
    source: Any
) : Event(source) {

    data class PositionOpenedEvent(
        val source: Position
    ) : PositionEvent(source)

    data class PositionClosedEvent(
        val source: Pair<Position, String>
    ) : PositionEvent(source)
}