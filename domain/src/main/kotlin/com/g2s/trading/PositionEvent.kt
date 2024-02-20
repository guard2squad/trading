package com.g2s.trading

import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionRefreshData
import org.springframework.context.ApplicationEvent

sealed class PositionEvent(
    source: Any
) : ApplicationEvent(source) {
    data class PositionOpenedEvent(
        val source: Position
    ) : PositionEvent(source)

    data class PositionClosedEvent(
        val source: Position
    ) : PositionEvent(source)
}
