package com.g2s.trading.controller

<<<<<<< HEAD
=======
import com.g2s.trading.NewSimpleCloseMan
>>>>>>> 19b89eaba9c8af27dfb6baa43878a81025b702e9
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
    val positionUseCase: PositionUseCase,
    val closeMan: NewSimpleCloseMan
) {

    @GetMapping("/test")
    fun test() {
        val testNode = ObjectMapperProvider.get().createObjectNode()
        val position = Position(
            strategyKey = "simple",
            symbol = Symbol.BTCUSDT,
            orderSide = OrderSide.SHORT,
            orderType = OrderType.MARKET,
            entryPrice = 0.0,   // 주문 후 updated
            positionAmt = 0.05,  // if positionAmt is 0.0 it is closed
            referenceData = testNode
        )
        positionUseCase.openPosition(position)
    }

    @GetMapping("/test/close")
    fun testClose() {
        closeMan.testPositionClosing(Symbol.BTCUSDT)
    }
}
