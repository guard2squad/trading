package com.g2s.trading.strategy.singlecandle

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.account.Money
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.event.Event
import com.g2s.trading.event.OrderEvent
import com.g2s.trading.event.PositionEvent
import com.g2s.trading.event.TradingEvent
import com.g2s.trading.indicator.CandleStick
import com.g2s.trading.indicator.CandleStickUpdateResult
import com.g2s.trading.indicator.LastCandles
import com.g2s.trading.order.*
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.AnalyzeReport
import com.g2s.trading.strategy.Strategy
import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategyType
import com.g2s.trading.symbol.Symbol
import com.g2s.trading.symbol.SymbolUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KClass


@Component
class SingleCandleStrategy(
    private val accountUseCase: AccountUseCase,
    private val symbolUseCase: SymbolUseCase,
    private val orderUseCase: OrderUseCase,
    private val positionUseCase: PositionUseCase,
) : Strategy {

    private val logger = LoggerFactory.getLogger(SingleCandleStrategy::class.java)
    private var orderMode = OrderMode.NORMAL

    override fun changeOrderMode(orderMode: String) {
        val newOrderMode = try {
            OrderMode.valueOf(orderMode)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid order mode: $orderMode")
        }
        this.orderMode = newOrderMode
    }

    override fun getType(): StrategyType {
        return StrategyType.SINGLE_CANDLE
    }

    override fun getTriggerEventTypes(): List<KClass<out Event>> {
        return listOf(
            TradingEvent.CandleStickEvent::class,
            PositionEvent.PositionOpenedEvent::class,
            PositionEvent.PositionClosedEvent::class,
            OrderEvent.OrderImmediatelyTriggerEvent::class
        )
    }

    override fun handle(event: Event, spec: StrategySpec) {
        when (event) {
            is TradingEvent.CandleStickEvent -> open(event, spec)
            is PositionEvent.PositionOpenedEvent -> close(event, spec)
            is PositionEvent.PositionClosedEvent -> cancelOppositeOrder(event, spec)
            is OrderEvent.OrderImmediatelyTriggerEvent -> reClose(event, spec)

            else -> throw RuntimeException("invalid event type: $event")
        }
    }

    private fun open(event: TradingEvent.CandleStickEvent, spec: StrategySpec) {
        val candleStick = event.source
        logger.debug("캔들 이벤트: ${candleStick.symbol.value}")

        // check symbol
        if (!spec.symbols.contains(candleStick.symbol.value)) {
            return
        }

        // symbol lock
        val canUseSymbol = symbolUseCase.useSymbol(candleStick.symbol)
        if (!canUseSymbol) {
            logger.debug("심볼 사용 중: ${candleStick.symbol.value}")
            return
        }
        // 스펙에 따라 심볼에 할당된 금액
        val allocatedAmount =
            (accountUseCase.getAccount().totalBalance * BigDecimal(spec.allocatedRatio))
                .divide(BigDecimal(spec.symbols.size), candleStick.symbol.quotePrecision, RoundingMode.HALF_UP)
        // (거래소 제약)심볼에 허용된 최소 주문 금액
        val minOrderAmount = BigDecimal(candleStick.symbol.minimumNotionalValue)
        // 예상 포지션 출금액 == Max(스펙에 따라 심볼에 할당된 금액, 예상 포지션 가치(마진) / 레버리지)
        val expectedWithdrawalAmount = allocatedAmount.max(
            minOrderAmount.divide(
                BigDecimal(candleStick.symbol.leverage),
                candleStick.symbol.quotePrecision,
                RoundingMode.HALF_UP
            )
        )
        // 예상 포지션 명목 가치 == 예상 포지션 출금액 * 레버리지
        val expectedPositionAmount = expectedWithdrawalAmount.multiply(
            BigDecimal(candleStick.symbol.leverage),
            MathContext(candleStick.symbol.quotePrecision, RoundingMode.HALF_UP)
        )
        // 예상 수수료
        val expectedFee = expectedPositionAmount * BigDecimal(candleStick.symbol.commissionRate) * BigDecimal(2)
        val money = accountUseCase.withdraw(expectedWithdrawalAmount, expectedFee)
        when (money) {
            is Money.NotAvailableMoney -> {
                logger.info("not available money: ${candleStick.symbol.value}, reason: ${money.reason}")
                logger.info(accountUseCase.getAccount().toString())
                symbolUseCase.unUseSymbol(candleStick.symbol)
                return
            }

            is Money.AvailableMoney -> {
                logger.debug(
                    "${candleStick.symbol.value} AvailableMoney 출금: " + money.toString() + " 계좌: " + accountUseCase.getAccount()
                        .toString()
                )
                val updateResult = LastCandles.update(candleStick)
                when (updateResult) {
                    is CandleStickUpdateResult.Failed -> {
                        // 출금 취소
                        accountUseCase.undoWithdrawal(money)
                        symbolUseCase.unUseSymbol(candleStick.symbol)
                        return
                    }

                    is CandleStickUpdateResult.Success -> {
                        // 캔들스틱 유효성 검증
                        if (!isValidCandleStick(updateResult.old, updateResult.new)) {
                            // 출금 취소
                            accountUseCase.undoWithdrawal(money)
                            symbolUseCase.unUseSymbol(candleStick.symbol)
                            return
                        }

                        // 전략으로 오픈여부 판단
                        val hammerRatio = spec.op["hammerRatio"].asDouble()
                        val takeProfitFactor = spec.op["takeProfitFactor"].asDouble()
                        val stopLossFactor = spec.op["stopLossFactor"].asDouble()
                        when (val analyzeReport =
                            analyze(updateResult.old, hammerRatio, takeProfitFactor, stopLossFactor, spec)) {
                            is AnalyzeReport.NonMatchingReport -> {
                                logger.info("non-matching-report: ${candleStick.symbol.value}, reason: ${analyzeReport.reason}")
                                // 출금 취소
                                accountUseCase.undoWithdrawal(money)
                                symbolUseCase.unUseSymbol(candleStick.symbol)
                            }

                            is AnalyzeReport.MatchingReport -> {
                                val quantity = quantity(
                                    amount = expectedPositionAmount,
                                    expectedEntryPrice = BigDecimal(analyzeReport.referenceData["expectedEntryPrice"].asDouble()),
                                    takeProfitFactor = takeProfitFactor,
                                    stopLossFactor = stopLossFactor,
                                    tailLength = analyzeReport.referenceData["tailLength"].asDouble(),
                                    symbol = candleStick.symbol,
                                    orderSide = analyzeReport.orderSide
                                )
                                if (quantity == 0.0) {
                                    accountUseCase.undoWithdrawal(money)
                                    symbolUseCase.unUseSymbol(candleStick.symbol)
                                    return
                                }
                                val order = OpenOrder.MarketOrder(
                                    symbol = analyzeReport.symbol,
                                    quantity = quantity,
                                    side = analyzeReport.orderSide,
                                    entryPrice = analyzeReport.referenceData["expectedEntryPrice"].asDouble(),
                                    referenceData = analyzeReport.referenceData,
                                )
                                orderUseCase.sendOrder(order)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun close(event: PositionEvent.PositionOpenedEvent, spec: StrategySpec) {
        val position = event.source
        val strategyKey = position.referenceData["strategyKey"].asText()
        if (strategyKey != spec.strategyKey) {
            return
        }
        val side = position.side
        val takeProfitPrice = position.referenceData["takeProfitPrice"].asDouble()
        val stopLossPrice = position.referenceData["stopLossPrice"].asDouble()

        val takeProfitOrder = CloseOrder.TakeProfitOrder(
            symbol = position.symbol,
            price = takeProfitPrice,
            quantity = position.quantity,
            side = closeSide(side),
            positionId = position.positionId
        )

        val stopLossOrder = CloseOrder.StopLossOrder(
            symbol = position.symbol,
            price = stopLossPrice,
            quantity = position.quantity,
            side = closeSide(side),
            positionId = position.positionId
        )

        orderUseCase.sendOrder(takeProfitOrder, stopLossOrder)
    }

    private fun closeSide(openSide: OrderSide): OrderSide {
        return when (openSide) {
            OrderSide.LONG -> OrderSide.SHORT
            OrderSide.SHORT -> OrderSide.LONG
        }
    }

    private fun cancelOppositeOrder(event: PositionEvent.PositionClosedEvent, spec: StrategySpec) {
        val position = event.source.first
        val strategyKey = position.referenceData["strategyKey"].asText()
        if (strategyKey != spec.strategyKey) {
            return
        }
        val filledOrderId = event.source.second
        val oppositeOrderId = position.closeOrderIds.filterNot { it == filledOrderId }.firstOrNull()
        if (oppositeOrderId != null) {
            val order = Order.CancelOrder(oppositeOrderId, position.symbol)
            symbolUseCase.unUseSymbol(position.symbol)
            orderUseCase.sendOrder(order)
        } else {
            throw RuntimeException("can not find opposite order by filledOrderId: $filledOrderId")
        }
    }

    private fun reClose(event: OrderEvent.OrderImmediatelyTriggerEvent, spec: StrategySpec) {
        val closeOrder = event.source
        val position = positionUseCase.findPosition(closeOrder.positionId)
        position?.let {
            val strategyKey = position.referenceData["strategyKey"].asText()
            if (strategyKey != spec.strategyKey) {
                return
            }
            val old = event.source
            val new = CloseOrder.MarketOrder(
                symbol = old.symbol,
                price = old.price,
                quantity = old.quantity,
                side = old.side,
                positionId = old.positionId
            )
            orderUseCase.sendOrder(new)
        }
    }

    private fun isValidCandleStick(old: CandleStick, new: CandleStick): Boolean {
        val now = Instant.now().toEpochMilli()
        val oneSecond = 1000L
        val oneMinute = 60 * oneSecond
        // 이전 꺼랑 1분 차이
        if (new.openTime - old.openTime != oneMinute) {
//            logger.info("이전과 같은 1분봉: ${old.symbol.value}  갱신: ${new.openTime}, 이전: ${old.openTime}")
            return false
        }

        // 새로 열린지 1초 이하
        if (now - new.openTime > oneSecond) {
            return false
        }
        logger.info("${old.symbol.value} 현재 시간 - 캔들 스틱 오픈 타임: ${(now - new.openTime) / 1000}")
        return true
    }

    private fun analyze(
        candleStick: CandleStick,
        hammerRatio: Double,
        takeProfitFactor: Double,
        stopLossFactor: Double,
        spec: StrategySpec
    ): AnalyzeReport {

        val tailTop = BigDecimal(candleStick.high)
        val tailBottom = BigDecimal(candleStick.low)
        val bodyTop = BigDecimal(max(candleStick.open, candleStick.close))
        val bodyBottom = BigDecimal(min(candleStick.open, candleStick.close))
        val totalLength = tailTop - tailBottom
        val bodyLength = bodyTop - bodyBottom
        val operationalHammerRatio = BigDecimal(hammerRatio)    // 운영값 : default 2
        val decimalTakeProfitFactor = BigDecimal(takeProfitFactor)    // 운영값
        val decimalStopLossFactor = BigDecimal(stopLossFactor)
        if (bodyLength.compareTo(BigDecimal.ZERO) == 0) {
            return AnalyzeReport.NonMatchingReport("바디 길이 0")
        }

        if ((bodyLength.divide(totalLength, 3, RoundingMode.HALF_UP)) <= BigDecimal(0.15)) {
            return AnalyzeReport.NonMatchingReport("바디 / 전체 <= 15%")
        }

        val pattern = SingleCandlePattern.entries.find { it.matches(tailTop, bodyTop, tailBottom, bodyBottom) }

        val report: AnalyzeReport
        when (pattern) {
            SingleCandlePattern.TOP_TAIL -> {
                val topTailLength = tailTop - bodyTop
                val calculatedHammerRatio = topTailLength / bodyLength
                val canOpen = calculatedHammerRatio > operationalHammerRatio
                if (!canOpen) {
                    return AnalyzeReport.NonMatchingReport("해머 비율에 미달")
                }

                val takeProfitPrice = closePrice(
                    expectedEntryPrice = bodyTop,
                    tailLength = topTailLength,
                    takeProfitFactor = decimalTakeProfitFactor,
                    stopLossFactor = decimalStopLossFactor,
                    side = OrderSide.LONG,
                    symbol = candleStick.symbol,
                    closeType = object : TypeReference<CloseOrder.TakeProfitOrder>() {}
                )
                val stopLossPrice = closePrice(
                    expectedEntryPrice = bodyTop,
                    tailLength = topTailLength,
                    takeProfitFactor = decimalTakeProfitFactor,
                    stopLossFactor = decimalStopLossFactor,
                    side = OrderSide.LONG,
                    symbol = candleStick.symbol,
                    closeType = object : TypeReference<CloseOrder.StopLossOrder>() {}
                )
                val isPositivePnL = isPositivePnL(
                    symbol = candleStick.symbol,
                    open = bodyTop,
                    close = BigDecimal(takeProfitPrice),
                    spec = spec
                )
                if (!isPositivePnL) {
                    return AnalyzeReport.NonMatchingReport("PNL 미달")
                }
                val referenceData =
                    createReferenceData(
                        candleStick = candleStick,
                        spec = spec,
                        tailLength = topTailLength,
                        pattern = pattern,
                        expectedEntryPrice = bodyTop.toDouble(),
                        takeProfitPrice = takeProfitPrice,
                        stopLossPrice = stopLossPrice,
                        calculatedHammerRatio = calculatedHammerRatio.toDouble()
                    )
                report = AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.LONG, referenceData)
            }

            SingleCandlePattern.BOTTOM_TAIL -> {
                val bottomTailLength = bodyBottom - tailBottom
                val calculatedHammerRatio = bottomTailLength / bodyLength
                val canOpen = calculatedHammerRatio > operationalHammerRatio
                if (!canOpen) {
                    return AnalyzeReport.NonMatchingReport("해머 비율에 미달")
                }

                val takeProfitPrice = closePrice(
                    expectedEntryPrice = bodyBottom,
                    tailLength = bottomTailLength,
                    takeProfitFactor = decimalTakeProfitFactor,
                    stopLossFactor = decimalStopLossFactor,
                    side = OrderSide.LONG,
                    symbol = candleStick.symbol,
                    closeType = object : TypeReference<CloseOrder.TakeProfitOrder>() {}
                )
                val stopLossPrice = closePrice(
                    expectedEntryPrice = bodyBottom,
                    tailLength = bottomTailLength,
                    takeProfitFactor = decimalTakeProfitFactor,
                    stopLossFactor = decimalStopLossFactor,
                    side = OrderSide.LONG,
                    symbol = candleStick.symbol,
                    closeType = object : TypeReference<CloseOrder.StopLossOrder>() {}
                )
                val isPositivePnL = isPositivePnL(
                    symbol = candleStick.symbol,
                    open = bodyBottom,
                    close = BigDecimal(takeProfitPrice),
                    spec = spec
                )
                if (!isPositivePnL) {
                    return AnalyzeReport.NonMatchingReport("PNL 미달")
                }
                val referenceData =
                    createReferenceData(
                        candleStick = candleStick,
                        spec = spec,
                        tailLength = bottomTailLength,
                        pattern = pattern,
                        expectedEntryPrice = bodyBottom.toDouble(),
                        takeProfitPrice = takeProfitPrice,
                        stopLossPrice = stopLossPrice,
                        calculatedHammerRatio = calculatedHammerRatio.toDouble()
                    )
                report = AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.LONG, referenceData)
            }

            SingleCandlePattern.MIDDLE_HIGH_TAIL -> {
                val highTailLength = tailTop - bodyTop
                val calculatedHammerRatio = highTailLength / bodyLength
                val canOpen = calculatedHammerRatio > operationalHammerRatio
                if (!canOpen) {
                    return AnalyzeReport.NonMatchingReport("해머 비율에 미달")
                }

                val takeProfitPrice = closePrice(
                    expectedEntryPrice = bodyTop,
                    tailLength = highTailLength,
                    takeProfitFactor = decimalTakeProfitFactor,
                    stopLossFactor = decimalStopLossFactor,
                    side = OrderSide.LONG,
                    symbol = candleStick.symbol,
                    closeType = object : TypeReference<CloseOrder.TakeProfitOrder>() {}
                )
                val stopLossPrice = closePrice(
                    expectedEntryPrice = bodyTop,
                    tailLength = highTailLength,
                    takeProfitFactor = decimalTakeProfitFactor,
                    stopLossFactor = decimalStopLossFactor,
                    side = OrderSide.LONG,
                    symbol = candleStick.symbol,
                    closeType = object : TypeReference<CloseOrder.StopLossOrder>() {}
                )
                val isPositivePnL = isPositivePnL(
                    symbol = candleStick.symbol,
                    open = bodyTop,
                    close = BigDecimal(takeProfitPrice),
                    spec = spec
                )
                if (!isPositivePnL) {
                    return AnalyzeReport.NonMatchingReport("PNL 미달")
                }
                val referenceData =
                    createReferenceData(
                        candleStick = candleStick,
                        spec = spec,
                        tailLength = highTailLength,
                        pattern = pattern,
                        expectedEntryPrice = bodyTop.toDouble(),
                        takeProfitPrice = takeProfitPrice,
                        stopLossPrice = stopLossPrice,
                        calculatedHammerRatio = calculatedHammerRatio.toDouble()
                    )
                report = AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.LONG, referenceData)
            }

            SingleCandlePattern.MIDDLE_LOW_TAIL -> {
                val lowTailLength = bodyBottom - tailBottom
                val calculatedHammerRatio = lowTailLength / bodyLength
                val canOpen = calculatedHammerRatio > operationalHammerRatio
                if (!canOpen) {
                    return AnalyzeReport.NonMatchingReport("해머 비율에 미달")
                }

                val takeProfitPrice = closePrice(
                    expectedEntryPrice = bodyBottom,
                    tailLength = lowTailLength,
                    takeProfitFactor = decimalTakeProfitFactor,
                    stopLossFactor = decimalStopLossFactor,
                    side = OrderSide.LONG,
                    symbol = candleStick.symbol,
                    closeType = object : TypeReference<CloseOrder.TakeProfitOrder>() {}
                )
                val stopLossPrice = closePrice(
                    expectedEntryPrice = bodyBottom,
                    tailLength = lowTailLength,
                    takeProfitFactor = decimalTakeProfitFactor,
                    stopLossFactor = decimalStopLossFactor,
                    side = OrderSide.LONG,
                    symbol = candleStick.symbol,
                    closeType = object : TypeReference<CloseOrder.StopLossOrder>() {}
                )
                val isPositivePnL = isPositivePnL(
                    symbol = candleStick.symbol,
                    open = bodyBottom,
                    close = BigDecimal(takeProfitPrice),
                    spec = spec
                )
                if (!isPositivePnL) {
                    return AnalyzeReport.NonMatchingReport("PNL 미달")
                }
                val referenceData =
                    createReferenceData(
                        candleStick = candleStick,
                        spec = spec,
                        tailLength = lowTailLength,
                        pattern = pattern,
                        expectedEntryPrice = bodyBottom.toDouble(),
                        takeProfitPrice = takeProfitPrice,
                        stopLossPrice = stopLossPrice,
                        calculatedHammerRatio = calculatedHammerRatio.toDouble()
                    )
                report = AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.LONG, referenceData)
            }

            null -> {
                return AnalyzeReport.NonMatchingReport("매칭에 해당 안 됨")
            }
        }
        return report
    }

    private fun closePrice(
        expectedEntryPrice: BigDecimal,
        tailLength: BigDecimal, // referenceData["tailLength"]
        takeProfitFactor: BigDecimal,
        stopLossFactor: BigDecimal,
        side: OrderSide,
        symbol: Symbol,
        closeType: TypeReference<out CloseOrder>
    ): Double {
        val price = when (closeType.type) {
            CloseOrder.TakeProfitOrder::class.java -> {
                when (side) {
                    OrderSide.LONG -> {
                        expectedEntryPrice + tailLength * takeProfitFactor
                    }

                    OrderSide.SHORT -> {
                        expectedEntryPrice - tailLength * takeProfitFactor
                    }
                }
            }

            CloseOrder.StopLossOrder::class.java -> {
                when (side) {
                    OrderSide.LONG -> {
                        expectedEntryPrice - tailLength * stopLossFactor
                    }

                    OrderSide.SHORT -> {
                        expectedEntryPrice + tailLength * stopLossFactor
                    }
                }
            }

            else -> throw RuntimeException("close order type error")
        }

        val precisePrice = price.setScale(symbol.pricePrecision, RoundingMode.CEILING)
        val tickSize = BigDecimal.valueOf(symbol.tickSize)
        val remainder = precisePrice.remainder(tickSize)
        // price % tickSize == 0
        if (remainder > BigDecimal.ZERO) {
            return (precisePrice - remainder + tickSize).toDouble()
        }
        // TODO: 최대가격, 최소가격 필터 고려하기

        return precisePrice.toDouble()
    }

    private fun createReferenceData(
        candleStick: CandleStick,
        spec: StrategySpec,
        tailLength: BigDecimal,
        pattern: SingleCandlePattern,
        expectedEntryPrice: Double,
        takeProfitPrice: Double,
        stopLossPrice: Double,
        calculatedHammerRatio: Double,
    ): ObjectNode {
        val referenceData =
            ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java) as ObjectNode
        referenceData.put("strategyType", spec.strategyType.toString())
        referenceData.put("strategyKey", spec.strategyKey)
        referenceData.put("candleStickPattern", pattern.toString())
        referenceData.set<DoubleNode>("tailLength", DoubleNode(tailLength.toDouble()))
        referenceData.set<DoubleNode>("expectedEntryPrice", DoubleNode(expectedEntryPrice))
        referenceData.set<DoubleNode>("takeProfitPrice", DoubleNode(takeProfitPrice))
        referenceData.set<DoubleNode>("stopLossPrice", DoubleNode(stopLossPrice))
        referenceData.set<DoubleNode>("calculatedHammerRatio", DoubleNode(calculatedHammerRatio))

        return referenceData
    }

    // 예상 손절 가격, 예상 익절 가격, 예상 구매 가격 중 가장 큰 값을 찾아, 예상 포지션 명목 가치(마진 * 레버리지)에서 나눠 수량을 구함
    private fun quantity(
        amount: BigDecimal,
        expectedEntryPrice: BigDecimal,
        takeProfitFactor: Double,
        stopLossFactor: Double,
        tailLength: Double,
        symbol: Symbol,
        orderSide: OrderSide
    ): Double {
        // "code":-4164,"msg":"Order's notional must be no smaller than 100 (unless you choose reduce only)."
        // 수량이 부족하다는 이유로 예외가 너무 자주 떠서 올림으로 처리함
        val quantityPrecision = symbol.quantityPrecision
        when (orderSide) {
            OrderSide.LONG -> {
                val maxPrice = maxOf(
                    expectedEntryPrice,
                    expectedEntryPrice + (BigDecimal(tailLength) * BigDecimal(takeProfitFactor)),
                    expectedEntryPrice - (BigDecimal(tailLength) * BigDecimal(stopLossFactor))
                )

                return amount.divide(
                    maxPrice,
                    quantityPrecision,
                    RoundingMode.CEILING
                ).toDouble()
            }

            OrderSide.SHORT -> {
                val maxPrice = maxOf(
                    expectedEntryPrice,
                    expectedEntryPrice - (BigDecimal(tailLength) * BigDecimal(takeProfitFactor)),
                    expectedEntryPrice + (BigDecimal(tailLength) * BigDecimal(stopLossFactor))
                )

                return amount.divide(
                    maxPrice,
                    quantityPrecision,
                    RoundingMode.CEILING
                ).toDouble()
            }
        }
    }

    private fun isPositivePnL(symbol: Symbol, open: BigDecimal, close: BigDecimal, spec: StrategySpec): Boolean {
        val commissionRate = symbol.commissionRate
        val pnl = (open - close).abs()
        val fee = (open + close).multiply(BigDecimal(commissionRate))
        val priceChangeRate = priceChangeRate(open, close)

        return pnl > fee && priceChangeRate > spec.op["priceChangeRate"].asDouble() // %단위
    }

    private fun priceChangeRate(open: BigDecimal, close: BigDecimal): Double {
        return (close - open).abs().divide(open, MathContext.DECIMAL128).multiply(BigDecimal(100)).toDouble()
    }

    enum class OrderMode {
        NORMAL,
        MINIMUM_QUANTITY
    }
}