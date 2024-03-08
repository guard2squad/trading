package com.g2s.trading.controller

import com.g2s.trading.ManualTrader
import com.g2s.trading.MarkPriceUseCase
import com.g2s.trading.NewTestSimpleOpenMan
import com.g2s.trading.account.Asset
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategySpecServiceStatus
import com.g2s.trading.symbol.Symbol
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
    val manualTrader: ManualTrader,
    val newTestSimpleOpenMan: NewTestSimpleOpenMan
) {
    // 최소 가치(MIN_NOTIONAL) 주문
    // Test : positionUseCase.openPosition
    @GetMapping("/open")
    fun testOpen(@RequestParam(name = "symbol") symbolValue: String) {
        // testSpec
        val emptyJsonNode = ObjectMapperProvider.get().createArrayNode()
        val tempSpec = StrategySpec(
            strategyKey = "manual",
            strategyType = "manual",
            symbols = Symbol.entries.toList(),
            asset = Asset.USDT,
            allocatedRatio = 0.25,
            op = emptyJsonNode,
            trigger = "",
            status = StrategySpecServiceStatus.SERVICE
        )

        val testNode = ObjectMapperProvider.get().createObjectNode()
        val symbol = Symbol.valueOf(symbolValue)
        val markPrice = markPriceUseCase.getMarkPrice(symbol).price
        val position = Position(
            strategyKey = tempSpec.strategyKey,
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
        manualTrader.manuallyOpenPosition(position, tempSpec)
    }

    // one spec multi symbol test
    @GetMapping("/open/symbols")
    fun testOneSpecMultiSymbol() {

        // test spec :  ETHUSDT open
        val emptyJsonNode = ObjectMapperProvider.get().createArrayNode()
        val tempSpec = StrategySpec(
            strategyKey = "test",
            strategyType = "test",
            symbols = Symbol.entries.toList(),
            asset = Asset.USDT,
            allocatedRatio = 0.25,
            op = emptyJsonNode,
            trigger = "",
            status = StrategySpecServiceStatus.SERVICE
        )

        val testNode = ObjectMapperProvider.get().createObjectNode()
        val symbol = Symbol.ETHUSDT
        val markPrice = markPriceUseCase.getMarkPrice(symbol).price
        val position = Position(
            strategyKey = tempSpec.strategyKey,
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

        positionUseCase.openPosition(position, tempSpec)

        // test spec :  ETHUSDT open
        val tempCandleStick = CandleStick(
            symbol = Symbol.ETHUSDT,
            interval = Interval.ONE_MINUTE,
            key = 123,
            open = 123.0,
            high = 123.0,
            low = 123.0,
            close = 123.9,
            volume = 123.0,
            numberOfTrades = 1
        )
        newTestSimpleOpenMan.open(tempSpec, tempCandleStick)
        // test sepc : BTCUSDT open
        newTestSimpleOpenMan.open(tempSpec, tempCandleStick.copy(symbol = Symbol.BTCUSDT))
    }

    @GetMapping("/close")
    fun testClose(@RequestParam(name = "symbol") symbolValue: String) {
        // testSpec
        val emptyJsonNode = ObjectMapperProvider.get().createArrayNode()
        val tempSpec = StrategySpec(
            strategyKey = "manual",
            strategyType = "manual",
            symbols = Symbol.entries.toList(),
            asset = Asset.USDT,
            allocatedRatio = 0.25,
            op = emptyJsonNode,
            trigger = "",
            status = StrategySpecServiceStatus.SERVICE
        )
        manualTrader.manuallyClosePosition(Symbol.valueOf(symbolValue), tempSpec)
    }

    private fun quantity(minNotional: BigDecimal, markPrice: BigDecimal, quantityPrecision: Int): Double {
        return minNotional.divide(markPrice, quantityPrecision, RoundingMode.CEILING).toDouble()
    }
}
