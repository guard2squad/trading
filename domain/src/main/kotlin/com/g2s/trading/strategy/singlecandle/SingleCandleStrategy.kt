package com.g2s.trading.strategy.singlecandle

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.indicator.CandleStick
import com.g2s.trading.indicator.MarkPriceUseCase
import com.g2s.trading.strategy.AnalyzeReport
import com.g2s.trading.order.OrderSide
import com.g2s.trading.symbol.Symbol
import com.g2s.trading.account.Money
import com.g2s.trading.account.NewAccountUseCase
import com.g2s.trading.event.NewEvent
import com.g2s.trading.event.NewPositionEvent
import com.g2s.trading.event.NewTradingEvent
import com.g2s.trading.indicator.CandleStickUpdateResult
import com.g2s.trading.indicator.LastCandles
import com.g2s.trading.order.NewCloseOrder
import com.g2s.trading.order.NewOpenOrder
import com.g2s.trading.order.NewOrderUseCase
import com.g2s.trading.strategy.NewStrategy
import com.g2s.trading.strategy.NewStrategySpec
import com.g2s.trading.strategy.NewStrategyType
import com.g2s.trading.symbol.NewSymbolUseCase
import com.g2s.trading.position.NewPositionUseCase
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KClass

@Component
class SingleCandleStrategy(
    private val positionUseCase: NewPositionUseCase,
    private val accountUseCase: NewAccountUseCase,
    private val symbolUseCase: NewSymbolUseCase,
    private val markPriceUseCase: MarkPriceUseCase,
    private val orderUseCase: NewOrderUseCase
) : NewStrategy {
    private var orderMode = OrderMode.MINIMUM_QUANTITY

    override fun getType(): NewStrategyType {
        return NewStrategyType.SINGLE_CANDLE
    }

    override fun getTriggerEventTypes(): List<KClass<out NewEvent>> {
        return listOf(
            NewTradingEvent.CandleStickEvent::class,
            NewPositionEvent.PositionOpenedEvent::class
        )
    }

    override fun handle(event: NewEvent, spec: NewStrategySpec) {
        when (event) {
            is NewTradingEvent.CandleStickEvent -> open(event, spec)
            is NewPositionEvent.PositionOpenedEvent -> close(event, spec)

            else -> throw RuntimeException("invalid event type: $event")
        }
    }

    private fun open(event: NewTradingEvent.CandleStickEvent, spec: NewStrategySpec) {
        val candleStick = event.source

        // check symbol
        if (!spec.symbols.contains(candleStick.symbol)) {
            return
        }

        // symbol lock
        val canUseSymbol = symbolUseCase.useSymbol(candleStick.symbol)
        if (!canUseSymbol) return

        val money = accountUseCase.withdraw(spec)
        when (money) {
            is Money.NotAvailableMoney -> {
                symbolUseCase.unUseSymbol(candleStick.symbol)
            }

            is Money.AvailableMoney -> {
                val updateResult = LastCandles.update(candleStick)
                if (updateResult == CandleStickUpdateResult.Failed) {
                    symbolUseCase.unUseSymbol(candleStick.symbol)
                    return
                }
                val oldCandleStick = (updateResult as CandleStickUpdateResult.Success).old

                // TODO(테스트후 캔들스틱 유효성 검사 추가)

                // 오픈 가능한 symbol 확인
                val markPrice = markPriceUseCase.getMarkPrice(candleStick.symbol)
                val hammerRatio = spec.op["hammerRatio"].asDouble()
                val takeProfitFactor = spec.op["takeProfitFactor"].asDouble()
                val stopLossFactor = spec.op["stopLossFactor"].asDouble()
                when (val analyzeReport = analyze(oldCandleStick, hammerRatio)) {
                    is AnalyzeReport.NonMatchingReport -> {
                        symbolUseCase.unUseSymbol(event.source.symbol)
                    }

                    is AnalyzeReport.MatchingReport -> {
                        val order = NewOpenOrder.MarketOrder(
                            symbol = analyzeReport.symbol,
                            price = markPrice.price,
                            amount = quantity(
                                balance = money.amount,
                                minNotional = BigDecimal(symbolUseCase.getMinNotionalValue(analyzeReport.symbol)),
                                markPrice = BigDecimal(markPrice.price),
                                takeProfitFactor = takeProfitFactor,
                                stopLossFactor = stopLossFactor,
                                tailLength = analyzeReport.referenceData["tailLength"].asDouble(),
                                quantityPrecision = symbolUseCase.getQuantityPrecision(analyzeReport.symbol),
                                orderSide = analyzeReport.orderSide
                            ),
                            side = analyzeReport.orderSide,
                            referenceData = analyzeReport.referenceData,
                        )

                        orderUseCase.sendOrder(order)
                    }
                }
            }
        }
    }

    private fun close(event: NewPositionEvent.PositionOpenedEvent, spec: NewStrategySpec) {
        val position = event.source
        val side = position.openOrder.side

        val takeProfitFactor = spec.op["takeProfitFactor"].doubleValue()
        val stopLossFactor = spec.op["stopLossFactor"].doubleValue()

        val takeProfitOrder = NewCloseOrder.NewTakeProfitOrder(
            symbol = position.openOrder.symbol,
            price = closePrice(
                position.openOrder as NewOpenOrder.MarketOrder,
                takeProfitFactor,
                stopLossFactor,
                object : TypeReference<NewCloseOrder.NewTakeProfitOrder>() {}
            ),
            amount = position.openOrder.amount,
            side = closeSide(side),
            positionId = position.id
        )

        val stopLossOrder = NewCloseOrder.NewStopLossOrder(
            symbol = position.openOrder.symbol,
            price = closePrice(
                position.openOrder,
                takeProfitFactor,
                stopLossFactor,
                object : TypeReference<NewCloseOrder.NewStopLossOrder>() {}
            ),
            amount = position.openOrder.amount,
            side = closeSide(side),
            positionId = position.id
        )

        orderUseCase.sendOrder(takeProfitOrder, stopLossOrder)
    }

    private fun closePrice(
        order: NewOpenOrder.MarketOrder,
        takeProfitFactor: Double,
        stopLossFactor: Double,
        closeType: TypeReference<out NewCloseOrder>
    ): Double {
        val entryPrice = BigDecimal(order.price)
        val tailLength = BigDecimal(order.referenceData["tailLength"].doubleValue())
        val decimalTakeProfitFactor = BigDecimal(takeProfitFactor)
        val decimalStopLossFactor = BigDecimal(stopLossFactor)
        val price = when (closeType) {
            NewCloseOrder.NewTakeProfitOrder::class.java -> {
                when (order.side) {
                    OrderSide.LONG -> {
                        entryPrice + tailLength * decimalTakeProfitFactor
                    }

                    OrderSide.SHORT -> {
                        entryPrice - tailLength * decimalTakeProfitFactor
                    }
                }
            }

            NewCloseOrder.NewStopLossOrder::class.java -> {
                when (order.side) {
                    OrderSide.LONG -> {
                        entryPrice - tailLength * decimalStopLossFactor
                    }

                    OrderSide.SHORT -> {
                        entryPrice + tailLength * decimalStopLossFactor
                    }
                }
            }

            else -> throw RuntimeException("close order type error")
        }

        return price.toDouble()
    }

    private fun closeSide(openSide: OrderSide): OrderSide {
        return when (openSide) {
            OrderSide.LONG -> OrderSide.SHORT
            OrderSide.SHORT -> OrderSide.LONG
        }
    }

    private fun analyze(
        candleStick: CandleStick,
        hammerRatio: Double
    ): AnalyzeReport {

        val tailTop = BigDecimal(candleStick.high)
        val tailBottom = BigDecimal(candleStick.low)
        val bodyTop = BigDecimal(max(candleStick.open, candleStick.close))
        val bodyBottom = BigDecimal(min(candleStick.open, candleStick.close))
        val totalLength = tailTop - tailBottom
        val bodyLength = bodyTop - bodyBottom
        val operationalHammerRatio = BigDecimal(hammerRatio)    // 운영값 : default 2
        if (bodyLength.compareTo(BigDecimal.ZERO) == 0) {
            return AnalyzeReport.NonMatchingReport
        } else if ((bodyLength.divide(totalLength, 3, RoundingMode.HALF_UP)) <= BigDecimal(0.15)) {
            return AnalyzeReport.NonMatchingReport
        } else if (tailTop > bodyTop && tailBottom == bodyBottom) {
            val tailLength = tailTop - bodyTop  // tailLength = topTailLength
            val candleHammerRatio = tailLength / bodyLength

            if (candleHammerRatio > operationalHammerRatio) {
                val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                (referenceData as ObjectNode).set<DoubleNode>(
                    "tailLength",
                    DoubleNode(tailLength.toDouble())
                )
                // 예상 구매 가격: bodyTop, 예상 익절 가격: bodyTop + topTailLength * takeProfitFactor
                return AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.LONG, referenceData)
            }
        } else if (tailBottom < bodyBottom && tailTop == bodyTop) {
            val tailLength = bodyBottom - tailBottom
            val candleHammerRatio = tailLength / bodyLength

            if (candleHammerRatio > operationalHammerRatio) {
                val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                (referenceData as ObjectNode).set<DoubleNode>(
                    "tailLength",
                    DoubleNode(tailLength.toDouble())
                )
                return AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.LONG, referenceData)
            }
        } else {
            val highTailLength = tailTop - bodyTop
            val lowTailLength = bodyBottom - tailBottom

            if (highTailLength > lowTailLength) {
                // 예상 구매 가격: bodyTop, 예상 익절 가격: bodyTop + topTailLength * takeProfitFactor
                val candleHammerRatio = highTailLength / bodyLength
                if (candleHammerRatio > operationalHammerRatio) {
                    val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>(
                        "tailLength",
                        DoubleNode(highTailLength.toDouble())
                    )
                    return AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.LONG, referenceData)
                }
            } else {
                val candleHammerRatio = lowTailLength / bodyLength
                if (candleHammerRatio > operationalHammerRatio) {
                    val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>(
                        "tailLength",
                        DoubleNode(lowTailLength.toDouble())
                    )
                    return AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.LONG, referenceData)
                }
            }
        }

        return AnalyzeReport.NonMatchingReport
    }


    private fun quantity(
        balance: Double,
        minNotional: BigDecimal,
        markPrice: BigDecimal,
        takeProfitFactor: Double,
        stopLossFactor: Double,
        tailLength: Double,
        quantityPrecision: Int,
        orderSide: OrderSide
    ): Double {
        return when (orderMode) {
            OrderMode.MINIMUM_QUANTITY -> {
                // "code":-4164,"msg":"Order's notional must be no smaller than 100 (unless you choose reduce only)."
                // 수량이 부족하다는 이유로 예외가 너무 자주 떠서 올림으로 처리함
                val quantity = minNotional.divide(markPrice, quantityPrecision, RoundingMode.CEILING).toDouble()
                var takeProfitQuantity: Double
                var stopLossQuantity: Double
                when (orderSide) {
                    OrderSide.LONG -> {
                        takeProfitQuantity = minNotional.divide(
                            markPrice + (BigDecimal(tailLength) * BigDecimal(takeProfitFactor)),
                            quantityPrecision,
                            RoundingMode.CEILING
                        ).toDouble()

                        stopLossQuantity = minNotional.divide(
                            markPrice - (BigDecimal(tailLength) * BigDecimal(stopLossFactor)),
                            quantityPrecision,
                            RoundingMode.CEILING
                        ).toDouble()
                    }

                    OrderSide.SHORT -> {
                        takeProfitQuantity = minNotional.divide(
                            markPrice - (BigDecimal(tailLength) * BigDecimal(takeProfitFactor)),
                            quantityPrecision,
                            RoundingMode.CEILING
                        ).toDouble()

                        stopLossQuantity = minNotional.divide(
                            markPrice + (BigDecimal(tailLength) * BigDecimal(stopLossFactor)),
                            quantityPrecision,
                            RoundingMode.CEILING
                        ).toDouble()
                    }
                }
                maxOf(takeProfitQuantity, stopLossQuantity, quantity)
            }

            OrderMode.NORMAL -> {
                BigDecimal(balance).divide(markPrice, quantityPrecision, RoundingMode.DOWN).toDouble()
            }
        }
    }

    private fun isPositivePnl(symbol: Symbol, open: BigDecimal, close: BigDecimal): Boolean {
        val commissionRate = symbolUseCase.getCommissionRate(symbol)
        val pnl = (open - close).abs()
        val fee = (open + close).multiply(BigDecimal(commissionRate))

        return pnl > fee
    }

    enum class OrderMode {
        NORMAL,
        MINIMUM_QUANTITY
    }
}