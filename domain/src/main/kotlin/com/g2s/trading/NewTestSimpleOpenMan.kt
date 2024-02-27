package com.g2s.trading

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.indicator.indicator.CandleStick
import com.g2s.trading.lock.LockUsage
import com.g2s.trading.lock.LockUseCase
import com.g2s.trading.openman.AnalyzeReport
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.order.Symbol
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategySpecRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

@Component
class NewTestSimpleOpenMan(
    private val strategySpecRepository: StrategySpecRepository,
    private val lockUseCase: LockUseCase,
    private val positionUseCase: PositionUseCase,
    private val accountUseCase: AccountUseCase,
    private val markPriceUseCase: MarkPriceUseCase,
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    companion object {
        // 최소 수량 주문
        private val TYPE = "test"
        private val MAXIMUM_HAMMER_RATIO = BigDecimal(9999)
        private const val TAKER_FEE_RATE = 0.00045  // taker fee : 0.045%
    }

    private val specs: ConcurrentHashMap<String, StrategySpec> = ConcurrentHashMap<String, StrategySpec>().also { map ->
        map.putAll(strategySpecRepository.findAllServiceStrategySpecByType(TYPE).associateBy { it.strategyKey })
    }

    private val candleSticks: MutableMap<String, MutableMap<Symbol, CandleStick>> =
        mutableMapOf() // strategyKey to Map<Symbol, CandleStick>

    @EventListener
    fun handleStartStrategyEvent(event: StrategyEvent.StartStrategyEvent) {
        specs.computeIfAbsent(event.source.strategyKey) {
            event.source
        }
    }

    @EventListener
    fun handleStopStrategyEvent(event: StrategyEvent.StopStrategyEvent) {
        candleSticks.remove(event.source.strategyKey)
        specs.remove(event.source.strategyKey)
    }

    @EventListener
    fun handleUpdateStrategyEvent(event: StrategyEvent.UpdateStrategyEvent) {
        specs.replace(event.source.strategyKey, event.source)
    }

    @EventListener
    fun handleCandleStickEvent(event: TradingEvent.CandleStickEvent) {
        specs.values.parallelStream().forEach { spec ->
            open(spec, event.source)
        }
    }

    fun open(spec: StrategySpec, candleStick: CandleStick) {
        // lock 획득
        val acquired = lockUseCase.acquire(spec.strategyKey, LockUsage.OPEN)
        if (!acquired) return

        // 이미 포지션 있는지 확인
        if (positionUseCase.hasPosition(spec.strategyKey)) {
            lockUseCase.release(spec.strategyKey, LockUsage.OPEN)
            return
        }

        // spec에 운영된 symbol중에서 현재 포지션이 없는 symbol 확인
        val unUsedSymbols = spec.symbols - positionUseCase.getAllUsedSymbols()
        if (unUsedSymbols.isEmpty()) {
            lockUseCase.release(spec.strategyKey, LockUsage.OPEN)
            return
        }

        // account lock
        val accountAcquired = accountUseCase.acquire()
        if (!accountAcquired) {
            lockUseCase.release(spec.strategyKey, LockUsage.OPEN)
            return
        }

        // account sync check
        val accountSynced = accountUseCase.isSynced()
        if (!accountSynced) {
            accountUseCase.release()
            lockUseCase.release(spec.strategyKey, LockUsage.OPEN)
            return
        }

        // 계좌 잔고 확인
        val allocatedBalance =
            accountUseCase.getAllocatedBalancePerStrategy(spec.asset, spec.allocatedRatio)
        val availableBalance = accountUseCase.getAvailableBalance(spec.asset)

        if (allocatedBalance > availableBalance) {
            accountUseCase.release()
            lockUseCase.release(spec.strategyKey, LockUsage.OPEN)
            return
        }

        // update candleStick
        val strategyCandleSticks = candleSticks[spec.strategyKey]
        lateinit var oldCandleStick: CandleStick

        val shouldAnalyze = if (strategyCandleSticks == null) {
            candleSticks[spec.strategyKey] = mutableMapOf(candleStick.symbol to candleStick)
            false
        } else {
            val old = strategyCandleSticks[candleStick.symbol]
            strategyCandleSticks[candleStick.symbol] = candleStick
            if (old == null) {
                false
            } else {
                // 이미 가지고 있는 캔들스틱인 경우
                if (old.key == candleStick.key) {
                    logger.debug("same candlestick ${candleStick.symbol}")
                    false
                }
                // 1분 차이 캔들스틱이 아닌 경우
                else if (old.key + 60000L != candleStick.key) {
                    logger.debug("not updated candlestick. ${candleStick.symbol}")
                    false
                }
                // 갱신된지 1초 이상된 캔들스틱인 경우
                else if (Instant.now().toEpochMilli() - candleStick.key > 1000) {
                    logger.debug("out 1 second, ${candleStick.symbol}")
                    false
                } else {
                    logger.debug("in 1 second, ${candleStick.symbol}")
                    oldCandleStick = old
                    true
                }
            }
        }

        if (!shouldAnalyze) {
            accountUseCase.release()
            lockUseCase.release(spec.strategyKey, LockUsage.OPEN)
            return
        }

        // 오픈 가능한 symbol 확인
        val markPrice = markPriceUseCase.getMarkPrice(candleStick.symbol)
        val hammerRatio = spec.op["hammerRatio"].asDouble()
        val scale = spec.op["scale"].asDouble()
        val analyzeReport = analyze(oldCandleStick, hammerRatio, scale)
        when (analyzeReport) {
            is AnalyzeReport.NonMatchingReport -> {
                logger.debug("non matching report for symbol: ${candleStick.symbol}")
            }

            is AnalyzeReport.MatchingReport -> {
                // open position
                val position = Position(
                    strategyKey = spec.strategyKey,
                    symbol = analyzeReport.symbol,
                    orderSide = analyzeReport.orderSide,
                    orderType = OrderType.MARKET,
                    entryPrice = markPrice.price,
                    positionAmt = quantity(
                        BigDecimal(analyzeReport.symbol.minNotionalValue),
                        BigDecimal(markPrice.price),
                        analyzeReport.symbol.quantityPrecision
                    ),
                    referenceData = analyzeReport.referenceData,
                )
                logger.debug("openPosition strategyKey: ${position.strategyKey}, symbol: ${position.symbol}")
                positionUseCase.openPosition(position)
            }
        }

        accountUseCase.release()
        lockUseCase.release(spec.strategyKey, LockUsage.OPEN)
    }

    private fun analyze(
        candleStick: CandleStick,
        hammerRatio: Double,
        scale: Double
    ): AnalyzeReport {
        logger.debug("analyze... candleStick: $candleStick")
        val tailTop = BigDecimal(candleStick.high)
        val tailBottom = BigDecimal(candleStick.low)
        val bodyTop = BigDecimal(max(candleStick.open, candleStick.close))
        val bodyBottom = BigDecimal(min(candleStick.open, candleStick.close))
        val bodyLength = bodyTop - bodyBottom
        val decimalHammerRatio = BigDecimal(hammerRatio)
        val decimalScale = BigDecimal(scale)

        if (bodyLength.compareTo(BigDecimal.ZERO) == 0) {
            logger.debug("star candle")
            val topTailLength = tailTop - bodyTop
            val bottomTailLength = bodyTop - tailBottom
            if (topTailLength > bottomTailLength) {
                logger.debug("star top tail")
                val candleHammerRatio =
                    if (bottomTailLength.compareTo(BigDecimal.ZERO) != 0) topTailLength / bottomTailLength else MAXIMUM_HAMMER_RATIO
                logger.debug("calculated candleHammerRatio: $candleHammerRatio, topTailLength: $topTailLength, bottomTailLength: $bottomTailLength")
                if (candleHammerRatio > decimalHammerRatio && isPositivePnl(
                        bodyTop,
                        bodyTop.minus(topTailLength.multiply(decimalScale))
                    )
                ) {
                    logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>(
                        "tailLength",
                        DoubleNode(topTailLength.multiply(decimalScale).toDouble())
                    )
                    logger.debug("scaled tailLength: ${topTailLength.multiply(decimalScale)}")
                    return AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.SHORT, referenceData)
                }
            } else {
                logger.debug("star bottom tail")
                val candleHammerRatio =
                    if (topTailLength.compareTo(BigDecimal.ZERO) != 0) bottomTailLength / topTailLength else MAXIMUM_HAMMER_RATIO
                logger.debug("calculated candleHammerRatio: $candleHammerRatio, topTailLength: $topTailLength, bottomTailLength: $bottomTailLength")
                if (candleHammerRatio > decimalHammerRatio && isPositivePnl(
                        bodyTop,
                        bodyTop.plus(bottomTailLength.multiply(decimalScale))
                    )
                ) {
                    logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>(
                        "tailLength",
                        DoubleNode(bottomTailLength.multiply(decimalScale).toDouble())
                    )
                    logger.debug("scaled tailLength: ${bottomTailLength.multiply(decimalScale)}")
                    return AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.LONG, referenceData)
                }
            }
        } else if (tailTop > bodyTop && tailBottom == bodyBottom) {
            logger.debug("top tail")
            val tailLength = tailTop - bodyTop
            val candleHammerRatio = tailLength / bodyLength
            logger.debug("calculated candleHammerRatio: $candleHammerRatio, tailLength: $tailLength, bodyLength: $bodyLength")
            if (candleHammerRatio > decimalHammerRatio && isPositivePnl(
                    bodyTop,
                    bodyTop.minus(tailLength.multiply(decimalScale))
                )
            ) {
                logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                (referenceData as ObjectNode).set<DoubleNode>(
                    "tailLength",
                    DoubleNode(tailLength.multiply(decimalScale).toDouble())
                )
                logger.debug("scaled tailLength: ${tailLength.multiply(decimalScale)}")
                return AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.SHORT, referenceData)
            }
        } else if (tailBottom < bodyBottom && tailTop == bodyTop) {
            logger.debug("bottom tail")
            val tailLength = bodyBottom - tailBottom
            val candleHammerRatio = tailLength / bodyLength
            logger.debug("calculated candleHammerRatio: $candleHammerRatio, tailLength: $tailLength, bodyLength: $bodyLength")
            if (candleHammerRatio > decimalHammerRatio && isPositivePnl(
                    bodyBottom,
                    bodyBottom.plus(tailLength.multiply(decimalScale))
                )
            ) {
                logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                (referenceData as ObjectNode).set<DoubleNode>(
                    "tailLength",
                    DoubleNode(tailLength.multiply(decimalScale).toDouble())
                )
                logger.debug("scaled tailLength: ${tailLength.multiply(decimalScale)}")
                return AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.LONG, referenceData)
            }
        } else {
            logger.debug("middle tail")
            val highTailLength = tailTop - bodyTop
            val lowTailLength = bodyBottom - tailBottom

            if (highTailLength > lowTailLength) {
                logger.debug("middle high tail, highTail: $highTailLength, lowTail: $lowTailLength, bodyTail: $bodyLength")
                val candleHammerRatio = highTailLength / bodyLength
                logger.debug("calculated candleHammerRatio: $candleHammerRatio")
                if (candleHammerRatio > decimalHammerRatio && isPositivePnl(
                        bodyTop,
                        bodyTop.minus(highTailLength.multiply(decimalScale))
                    )
                ) {
                    logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>(
                        "tailLength",
                        DoubleNode(highTailLength.multiply(decimalScale).toDouble())
                    )
                    logger.debug("scaled tailLength: ${highTailLength.multiply(decimalScale)}")
                    return AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.SHORT, referenceData)
                }
            } else {
                logger.debug("middle low tail, highTail: $highTailLength, lowTail: $lowTailLength, bodyTail: $bodyLength")
                val candleHammerRatio = highTailLength / bodyLength
                logger.debug("calculated candleHammerRatio: $candleHammerRatio")
                if (candleHammerRatio > decimalHammerRatio && isPositivePnl(
                        bodyBottom,
                        bodyBottom.plus(lowTailLength.multiply(decimalScale))
                    )
                ) {
                    logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>(
                        "tailLength",
                        DoubleNode(lowTailLength.multiply(decimalScale).toDouble())
                    )
                    logger.debug("scaled tailLength: ${lowTailLength.multiply(decimalScale)}")
                    return AnalyzeReport.MatchingReport(candleStick.symbol, OrderSide.LONG, referenceData)
                }
            }
        }

        return AnalyzeReport.NonMatchingReport
    }

    private fun quantity(minNotional: BigDecimal, markPrice: BigDecimal, quantityPrecision: Int): Double {
        // "code":-4164,"msg":"Order's notional must be no smaller than 100 (unless you choose reduce only)."
        // 수량이 부족하다는 이유로 예외가 너무 자주 떠서 올림으로 처리함
        return minNotional.divide(markPrice, quantityPrecision, RoundingMode.CEILING).toDouble()
    }

    /*
        MARKET ORDER => taker
        taker fee : 0.045%
     */
    private fun isPositivePnl(open: BigDecimal, close: BigDecimal): Boolean {
        val pnl = (open - close).abs()
        val fee = (open + close).multiply(BigDecimal(TAKER_FEE_RATE))
        logger.debug("fee :${fee}, pnl :${pnl}")
        return pnl > fee
    }
}
