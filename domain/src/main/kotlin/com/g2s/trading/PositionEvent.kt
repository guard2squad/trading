package com.g2s.trading

import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionRefreshData
import org.springframework.context.ApplicationEvent

sealed class PositionEvent(
    source: Any
) : ApplicationEvent(source) {
    data class PositionOpenEvent(
        val source: Position
    ) : PositionEvent(source)

    data class PositionCloseEvent(
        val source: Position
    ) : PositionEvent(source)

    data class PositionRefreshEvent(
        val source: List<PositionRefreshData>
    ) : PositionEvent(source)

    data class PositionsLoadEvent(
        val source: List<Position>
    ) : PositionEvent(source)
}
