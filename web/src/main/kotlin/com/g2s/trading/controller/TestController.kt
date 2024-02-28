package com.g2s.trading.controller

import com.g2s.trading.MarkPriceUseCase
import com.g2s.trading.NewSimpleCloseMan
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.symbol.Symbol
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.symbol.SymbolUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode

@RestController
@RequestMapping("/test")
class TestController(
    val positionUseCase: PositionUseCase,
    val markPriceUseCase: MarkPriceUseCase,
    val symbolUseCase: SymbolUseCase,
    val closeMan: NewSimpleCloseMan,
) {

    // 최소 가치(MIN_NOTIONAL) 주문
    @GetMapping("/open")
    fun test(@RequestParam(name = "symbol") symbolValue: String) {
        val testNode = ObjectMapperProvider.get().createObjectNode()
        val symbol = Symbol.valueOf(symbolValue)
        val markPrice = markPriceUseCase.getMarkPrice(symbol).price
        val position = Position(
            strategyKey = "simple",
            symbol = symbol,
            orderSide = OrderSide.SHORT,
            orderType = OrderType.MARKET,
            entryPrice = markPrice,
            positionAmt = quantity(
                BigDecimal(symbolUseCase.getMinNotionalValue(symbol)),
                BigDecimal(markPrice),
                symbolUseCase.getQuantityPrecision(symbol)
            ),
            referenceData = testNode
        )
        positionUseCase.openPosition(position)
    }

    @GetMapping("/close")
    fun testClose() {
        closeMan.testPositionClosing(Symbol.BTCUSDT)
    }

    private fun quantity(minNotional: BigDecimal, markPrice: BigDecimal, quantityPrecision: Int): Double {
        return minNotional.divide(markPrice, quantityPrecision, RoundingMode.CEILING).toDouble()
    }
}
