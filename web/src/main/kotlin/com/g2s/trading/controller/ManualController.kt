package com.g2s.trading.controller

import com.g2s.trading.ManualTrader
import com.g2s.trading.indicator.MarkPriceUseCase
import com.g2s.trading.account.Asset
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.Position
import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategySpecServiceStatus
import com.g2s.trading.symbol.Symbol
import com.g2s.trading.symbol.SymbolUseCase
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode

@RestController
@RequestMapping("/manual")
class ManualController(
    val markPriceUseCase: MarkPriceUseCase,
    val symbolUseCase: SymbolUseCase,
    val manualTrader: ManualTrader
) {
    @PostMapping("market/open")
    fun openMinNotionalValuePosition(@RequestParam(name = "symbol") symbolValue: String) {
        // testSpec
        val emptyJsonNode = ObjectMapperProvider.get().createArrayNode()
        val tempSpec = StrategySpec(
            strategyKey = "manual",
            strategyType = "manual",
            symbols = listOf(Symbol.valueOf(symbolValue)),
            asset = Asset.USDT,
            allocatedRatio = 0.0,
            op = emptyJsonNode,
            trigger = "",
            status = StrategySpecServiceStatus.SERVICE
        )

        val testNode = ObjectMapperProvider.get().createObjectNode()
        val symbol = Symbol.valueOf(symbolValue)
        val markPrice = markPriceUseCase.getMarkPrice(symbol).price
        val position = Position(
            positionKey = Position.PositionKey(symbol, OrderSide.SHORT),
            strategyKey = tempSpec.strategyKey,
            symbol = symbol,
            orderSide = OrderSide.SHORT,
            orderType = OrderType.MARKET,
            entryPrice = markPrice,
            // 최소 가치(MIN_NOTIONAL) 주문
            positionAmt = BigDecimal(symbolUseCase.getMinNotionalValue(symbol)).divide(
                BigDecimal(markPrice),
                symbolUseCase.getQuantityPrecision(symbol),
                RoundingMode.CEILING
            ).toDouble(),
            asset = Asset.USDT,
            referenceData = testNode
        )
        manualTrader.manuallyOpenPosition(position, tempSpec)
    }

    @PostMapping("close")
    fun close(@RequestParam(name = "symbol") symbolValue: String) {
        // manualSpec
        val emptyJsonNode = ObjectMapperProvider.get().createArrayNode()
        val tempSpec = StrategySpec(
            strategyKey = "manual",
            strategyType = "manual",
            symbols = listOf(Symbol.valueOf(symbolValue)),
            asset = Asset.USDT,
            allocatedRatio = 0.0,
            op = emptyJsonNode,
            trigger = "",
            status = StrategySpecServiceStatus.SERVICE
        )
        manualTrader.manuallyClosePosition(Symbol.valueOf(symbolValue), tempSpec)
    }

    @PostMapping("limit/open")
    fun openByLimitOrder(@RequestParam(name = "symbol") symbolValue: String) {
        // manual spec
        val emptyJsonNode = ObjectMapperProvider.get().createArrayNode()
        val tempSpec = StrategySpec(
            strategyKey = "manual",
            strategyType = "manual",
            symbols = listOf(Symbol.valueOf(symbolValue)),
            asset = Asset.USDT,
            allocatedRatio = 0.0,
            op = emptyJsonNode,
            trigger = "MANUAL",
            status = StrategySpecServiceStatus.SERVICE
        )

        // manual reference data
        val manualReferenceData = ObjectMapperProvider.get().createObjectNode()
        val symbol = Symbol.valueOf(symbolValue)
        val markPrice = markPriceUseCase.getMarkPrice(symbol).price
        val position = Position(
            positionKey = Position.PositionKey(symbol, OrderSide.SHORT),
            strategyKey = tempSpec.strategyKey,
            symbol = symbol,
            orderSide = OrderSide.SHORT,
            orderType = OrderType.LIMIT,
            entryPrice = caculateEntryPrice(markPrice, symbol),
            // 최소 가치(MIN_NOTIONAL) 주문
            positionAmt = BigDecimal(symbolUseCase.getMinNotionalValue(symbol)).divide(
                BigDecimal(markPrice),
                symbolUseCase.getQuantityPrecision(symbol),
                RoundingMode.CEILING
            ).toDouble(),
            asset = Asset.USDT,
            referenceData = manualReferenceData
        )
        manualTrader.manuallyOpenPosition(position, tempSpec)
    }

    /**
     * Limit 주문 가격을 계산하는 함수. 입력된 가격을 암호화폐의 정확도(precision)에 맞게 조정하고,
     * 지정된 tickSize에 따라 가격을 조정합니다. 아래의 조건들을 만족하지 않을 경우 주문이 실패합니다:
     *
     * 1. (price - minPrice) % tickSize == 0
     *    - 이 조건을 만족하지 못할 경우, 가격이 tickSize의 배수로 증가하지 않음을 의미합니다.
     *    - 실패 응답: -4014 PRICE_NOT_INCREASED_BY_TICK_SIZE, msg: Price not increased by tick size.
     * 2. 가격의 정확도(precision)를 만족해야 함
     *    - 예) BTC: pricePrecision == 2, price=63976.15276916인 경우, 정확도가 8로 설정된 최대값을 초과합니다.
     *    - 실패 응답: -1111 BAD_PRECISION, msg: Precision is over the maximum defined for this asset.
     *
     * @param price 원래 가격(raw state)을 Double 형태로 입력받습니다.
     * @param symbol 자산의 식별자입니다. 조건을 구하기 위해 사용됩니다.
     * @return 조정된 가격을 Double 형태로 반환합니다.
     */
    private fun caculateEntryPrice(price: Double, symbol: Symbol): Double {
        // 정확도에 따라 버림처리
        val truncatedPrice = BigDecimal(price).setScale(symbolUseCase.getPricePrecision(symbol), RoundingMode.CEILING)
            .toDouble()

        // tickSize조건에 따라 가격 조정
        val tickSize = symbolUseCase.getTickSize(symbol)
        val minPrice = symbolUseCase.getMinPrice(symbol)
        val remainder = (truncatedPrice - minPrice) % tickSize
        val quotient = truncatedPrice - remainder
        if (remainder > tickSize / 2) {
            return quotient + tickSize
        }
        // TODO: 나머지를 롱인지, 숏인지에 따라서 올림, 버림해도 좋을 것 같음
        return quotient
    }
}
