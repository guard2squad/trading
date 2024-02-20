package com.g2s.trading.controller

import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.order.Symbol
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class TestController(
    val positionUseCase: PositionUseCase
) {

    @GetMapping("/test")
    fun test() {
        val testNode = ObjectMapperProvider.get().createObjectNode()
        val position = Position(
            strategyKey = "test",
            symbol = Symbol.BTCUSDT,
            entryPrice = 0.0,   // 주문 후 updated
            positionAmt = 0.01,  // if positionAmt is 0.0 it is closed
            orderSide = OrderSide.LONG,
            orderType = OrderType.MARKET,
            referenceData = testNode
        )
        positionUseCase.openPosition(position)
    }
}
