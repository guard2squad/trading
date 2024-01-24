package com.g2s.trading.order

import com.g2s.trading.Exchange
import com.g2s.trading.position.CloseReferenceData
import com.g2s.trading.position.Position
import com.g2s.trading.strategy.StrategySpec
import org.springframework.stereotype.Service

@Service
class OrderUseCase(
    private val exchangeImpl: Exchange
) {
    fun openOrder(order : Order, closeReferenceData: CloseReferenceData) : Position {
        val position = exchangeImpl.openPosition(order, closeReferenceData)
        return position
    }
}
