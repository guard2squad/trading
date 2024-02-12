package com.g2s.trading

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.common.ObjectMapperProvider
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

@Component
class NewSimpleOpenMan(
    private val strategySpecRepository: StrategySpecRepository,
    private val lockUseCase: LockUseCase,
    private val positionUseCase: PositionUseCase,
    private val accountUseCase: AccountUseCase,
    private val markPriceUseCase: MarkPriceUseCase
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val TYPE = "simple"
        private val MAXIMUM_HAMMER_RATIO = BigDecimal(9999)
        private const val LEVERAGE = 10
        private const val TAKER_FEE_RATE = 0.045
        private const val FEE_COUNT = 2   // Payments on opening and closing
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

    private fun open(spec: StrategySpec, candleStick: CandleStick) {
        // lock 획득
        val acquired = lockUseCase.acquire(spec.strategyKey, LockUsage.OPEN)
        if (!acquired) return

        // 이미 포지션 있는지 확인
        if (positionUseCase.hasPosition(spec.strategyKey)) {
            return
        }

        // spec에 운영된 symbol중에서 현재 포지션이 없는 symbol 확인
        val unUsedSymbols = spec.symbols - positionUseCase.getAllUsedSymbols()
        if (unUsedSymbols.isEmpty()) {
            return
        }

        // account lock
        val accountAcquired = accountUseCase.acquire()
        if (!accountAcquired) return

        // account sync check
        val accountSynced = accountUseCase.isSynced()
        if (!accountSynced) return

        // 계좌 잔고 확인
        val allocatedBalance =
            accountUseCase.getAllocatedBalancePerStrategy(spec.asset, spec.allocatedRatio)
        val availableBalance = accountUseCase.getAvailableBalance(spec.asset)

        if (allocatedBalance > availableBalance) return

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
                oldCandleStick = old
                old.key != candleStick.key
            }
        }

        if (!shouldAnalyze) return

        // 오픈 가능한 symbol 확인
        val markPrice = markPriceUseCase.getMarkPrice(candleStick.symbol)
        val hammerRatio = spec.op["hammerRatio"].asDouble()
        val analyzeReport = unUsedSymbols.asSequence()
            .map { unUsedSymbol -> analyze(oldCandleStick, markPrice, unUsedSymbol, hammerRatio) }
            .filterIsInstance<AnalyzeReport.MatchingReport>()
            .take(1)
            .firstOrNull() ?: return

        // open position
        val position = Position(
            strategyKey = spec.strategyKey,
            symbol = analyzeReport.symbol,
            orderSide = analyzeReport.orderSide,
            orderType = OrderType.MARKET,
            entryPrice = markPrice.price,
            positionAmt = quantity(allocatedBalance, BigDecimal(markPrice.price), analyzeReport.symbol.precision),
            referenceData = analyzeReport.referenceData,
        )
        positionUseCase.openPosition(position)

        lockUseCase.release(spec.strategyKey, LockUsage.OPEN)
    }

    private fun analyze(
        candleStick: CandleStick,
        markPrice: MarkPrice,
        symbol: Symbol,
        hammerRatio: Double
    ): AnalyzeReport {
        val tailTop = BigDecimal(candleStick.high)
        val tailBottom = BigDecimal(candleStick.low)
        val bodyTop = BigDecimal(max(candleStick.open, candleStick.close))
        val bodyBottom = BigDecimal(min(candleStick.open, candleStick.close))
        val bodyLength = bodyTop - bodyBottom
        val decimalHammerRatio = BigDecimal(hammerRatio)

        if (bodyLength.compareTo(BigDecimal.ZERO) == 0) {
            // star candle
            val topTailLength = tailTop - bodyTop
            val bottomTailLength = bodyTop - tailBottom
            if (topTailLength > bottomTailLength) {
                val candleHammerRatio =
                    if (bottomTailLength.compareTo(BigDecimal.ZERO) != 0) topTailLength / bottomTailLength else MAXIMUM_HAMMER_RATIO
                if (candleHammerRatio > decimalHammerRatio && getFeeCondition(markPrice, topTailLength, LEVERAGE)) {
                    logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(topTailLength.toDouble()))
                    return AnalyzeReport.MatchingReport(symbol, OrderSide.SHORT, referenceData)
                }
            } else {
                val candleHammerRatio =
                    if (topTailLength.compareTo(BigDecimal.ZERO) != 0) bottomTailLength / topTailLength else MAXIMUM_HAMMER_RATIO
                if (candleHammerRatio > decimalHammerRatio && getFeeCondition(markPrice, bottomTailLength, LEVERAGE)) {
                    logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(bottomTailLength.toDouble()))
                    return AnalyzeReport.MatchingReport(symbol, OrderSide.LONG, referenceData)
                }
            }
        } else if (tailTop > bodyTop && tailBottom == bodyBottom) {
            println("top tail")
            val tailLength = tailTop - bodyTop
            val candleHammerRatio = tailLength / bodyLength
            if (candleHammerRatio > decimalHammerRatio && getFeeCondition(markPrice, tailLength, LEVERAGE)) {
                logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(tailLength.toDouble()))
                return AnalyzeReport.MatchingReport(symbol, OrderSide.SHORT, referenceData)
            }
        } else if (tailBottom < bodyBottom && tailTop == bodyTop) {
            println("bottom tail")
            val tailLength = bodyBottom - tailBottom
            val candleHammerRatio = tailLength / bodyLength
            if (candleHammerRatio > decimalHammerRatio && getFeeCondition(markPrice, tailLength, LEVERAGE)) {
                logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(tailLength.toDouble()))
                return AnalyzeReport.MatchingReport(symbol, OrderSide.LONG, referenceData)
            }
        } else {
            println("middle tail")
            val highTailLength = tailTop - bodyTop
            val lowTailLength = bodyBottom - tailBottom

            if (highTailLength > lowTailLength) {
                println("middle high tail, highTail: $highTailLength, lowTail: $lowTailLength, bodyTail: $bodyLength")
                val candleHammerRatio = highTailLength / bodyLength
                if (candleHammerRatio > decimalHammerRatio && getFeeCondition(markPrice, highTailLength, LEVERAGE)) {
                    logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(highTailLength.toDouble()))
                    return AnalyzeReport.MatchingReport(symbol, OrderSide.SHORT, referenceData)
                }
            } else {
                println("middle low tail, highTail: $highTailLength, lowTail: $lowTailLength, bodyTail: $bodyLength")
                val candleHammerRatio = highTailLength / bodyLength
                if (candleHammerRatio > decimalHammerRatio && getFeeCondition(markPrice, lowTailLength, LEVERAGE)) {
                    logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(candleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(lowTailLength.toDouble()))
                    return AnalyzeReport.MatchingReport(symbol, OrderSide.LONG, referenceData)
                }
            }
        }

        return AnalyzeReport.NonMatchingReport
    }

    private fun quantity(balance: BigDecimal, markPrice: BigDecimal, precision: Int): Double {
        return balance.divide(markPrice, precision, RoundingMode.DOWN).toDouble()
    }

    /*
        MARKET ORDER => taker
        taker fee : 0.045%
     */
    private fun getFeeCondition(markPrice: MarkPrice, estimatedPriceChange: BigDecimal, leverage: Int): Boolean {
        val estimatedROE = estimatedPriceChange.divide(BigDecimal(markPrice.price))
        return estimatedROE.multiply(BigDecimal(100)) >
                BigDecimal(TAKER_FEE_RATE).multiply(BigDecimal(leverage)).multiply(BigDecimal(FEE_COUNT))
    }
}