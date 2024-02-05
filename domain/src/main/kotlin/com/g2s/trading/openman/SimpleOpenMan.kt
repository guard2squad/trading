package com.g2s.trading.openman

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.indicator.IndicatorUseCase
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.lock.LockUsage
import com.g2s.trading.lock.LockUseCase
import com.g2s.trading.openman.AnalyzeReport.*
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.order.OrderUseCase
import com.g2s.trading.order.Symbol
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.StrategySpec
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import kotlin.math.max
import kotlin.math.min

@Component
class SimpleOpenMan(
    private val positionUseCase: PositionUseCase,
    private val accountUseCase: AccountUseCase,
    private val orderUseCase: OrderUseCase,
    private val indicatorUseCase: IndicatorUseCase,
    private val lockUseCase: LockUseCase
) : OpenMan {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val MAXIMUM_HAMMER_RATIO = BigDecimal(9999)
        private const val LEVERAGE = 10
        private const val TAKER_FEE_RATE = 0.045
        private const val FEE_COUNT = 2   // Payments on opening and closing
    }

    override fun type() = "simple"

    override fun open(strategySpec: StrategySpec) {
        // lock
        val acquired = lockUseCase.acquire(strategySpec.strategyKey, LockUsage.OPEN)
        if (!acquired) return

        // 이미 포지션 있는지 확인
        if (positionUseCase.hasPosition(strategySpec.strategyKey)) {
            lockUseCase.release(strategySpec.strategyKey, LockUsage.OPEN)
            return
        }

        // spec에 운영된 symbol중에서 현재 포지션이 없는 symbol 확인
        val unUsedSymbols = strategySpec.symbols - positionUseCase.getAllUsedSymbols()
        if (unUsedSymbols.isEmpty()) {
            lockUseCase.release(strategySpec.strategyKey, LockUsage.OPEN)
            return
        }

        // 계좌 잔고 확인
        accountUseCase.syncAccount()
        val allocatedBalance =
            accountUseCase.getAllocatedBalancePerStrategy(strategySpec.asset, strategySpec.allocatedRatio)
        val availableBalance = accountUseCase.getAvailableBalance(strategySpec.asset)
        logger.info("allocated: $allocatedBalance, available: $availableBalance")
        if (allocatedBalance > availableBalance) {
            logger.info("not enough money")
            lockUseCase.release(strategySpec.strategyKey, LockUsage.OPEN)
            return
        }

        // 오픈 가능한 symbol 확인
        val hammerRatio = strategySpec.op["hammerRatio"].asDouble()
        val analyzeReport = unUsedSymbols.asSequence()
            .map { unUsedSymbol -> analyze(strategySpec, unUsedSymbol, hammerRatio) }
            .filterIsInstance<MatchingReport>()
            .take(1)
            .firstOrNull() ?: run {
            lockUseCase.release(strategySpec.strategyKey, LockUsage.OPEN)
            return
        }
        logger.info("analyzeReport: $analyzeReport")

        val lastPrice = indicatorUseCase.getLastPrice(analyzeReport.symbol)
        val order = orderUseCase.createOrder(
            strategySpec.strategyKey,
            lastPrice,
            allocatedBalance,
            analyzeReport.symbol,
            analyzeReport.orderSide,
            OrderType.MARKET
        )
        logger.info("order: $order, lastPrice: $lastPrice")

        val position = orderUseCase.openOrder(order)
            .apply {
                orderSide = order.orderSide
                orderType = order.orderType
                referenceData = analyzeReport.referenceData
            }
        logger.info("position: $position")
        positionUseCase.addPosition(strategySpec.strategyKey, position)
        positionUseCase.updateLastPosition(strategySpec.strategyKey, position)

        lockUseCase.release(strategySpec.strategyKey, LockUsage.OPEN)
    }

    private fun analyze(strategySpec: StrategySpec, symbol: Symbol, hammerRatio: Double): AnalyzeReport {
        val lastPrice = indicatorUseCase.getLastPrice(symbol) // 진입 예상 가격
        val candleSticks = indicatorUseCase.getCandleStick(symbol, Interval.ONE_MINUTE, 2)
        val lastCandleStick = candleSticks.first()

        positionUseCase.findLastPosition(strategySpec.strategyKey, symbol)?.let { lastPosition ->
            if (lastPosition.referenceData["key"].asLong() == lastCandleStick.key)
                logger.info("key duplicated : ${lastCandleStick.key}")
            return DuplicatedIndicatorReport
        }

        val tailTop = BigDecimal(lastCandleStick.high)
        val tailBottom = BigDecimal(lastCandleStick.low)
        val bodyTop = BigDecimal(max(lastCandleStick.open, lastCandleStick.close))
        val bodyBottom = BigDecimal(min(lastCandleStick.open, lastCandleStick.close))
        val bodyLength = bodyTop - bodyBottom
        val decimalHammerRatio = BigDecimal(hammerRatio)

        println("lastCandleStick: $lastCandleStick")

        if (bodyLength.compareTo(BigDecimal.ZERO) == 0) {
            println("star candle")
            val topTailLength = tailTop - bodyTop
            val bottomTailLength = bodyTop - tailBottom
            if (topTailLength > bottomTailLength) {
                val candleHammerRatio =
                    if (bottomTailLength.compareTo(BigDecimal.ZERO) != 0) topTailLength / bottomTailLength else MAXIMUM_HAMMER_RATIO
                if (candleHammerRatio > decimalHammerRatio && getFeeCondition(symbol, topTailLength, LEVERAGE)) {
                    logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(topTailLength.toDouble()))
                    return MatchingReport(symbol, OrderSide.SHORT, referenceData)
                }
            } else {
                val candleHammerRatio =
                    if (topTailLength.compareTo(BigDecimal.ZERO) != 0) bottomTailLength / topTailLength else MAXIMUM_HAMMER_RATIO
                if (candleHammerRatio > decimalHammerRatio && getFeeCondition(symbol, bottomTailLength, LEVERAGE)) {
                    logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(bottomTailLength.toDouble()))
                    return MatchingReport(symbol, OrderSide.LONG, referenceData)
                }
            }
        } else if (tailTop > bodyTop && tailBottom == bodyBottom) {
            println("top tail")
            val tailLength = tailTop - bodyTop
            val candleHammerRatio = tailLength / bodyLength
            if (candleHammerRatio > decimalHammerRatio && getFeeCondition(symbol, tailLength, LEVERAGE)) {
                logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                val referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
                (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(tailLength.toDouble()))
                return MatchingReport(symbol, OrderSide.SHORT, referenceData)
            }
        } else if (tailBottom < bodyBottom && tailTop == bodyTop) {
            println("bottom tail")
            val tailLength = bodyBottom - tailBottom
            val candleHammerRatio = tailLength / bodyLength
            if (candleHammerRatio > decimalHammerRatio && getFeeCondition(symbol, tailLength, LEVERAGE)) {
                logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                val referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
                (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(tailLength.toDouble()))
                return MatchingReport(symbol, OrderSide.LONG, referenceData)
            }
        } else {
            println("middle tail")
            val highTailLength = tailTop - bodyTop
            val lowTailLength = bodyBottom - tailBottom

            if (highTailLength > lowTailLength) {
                println("middle high tail, highTail: $highTailLength, lowTail: $lowTailLength, bodyTail: $bodyLength")
                val candleHammerRatio = highTailLength / bodyLength
                if (candleHammerRatio > decimalHammerRatio && getFeeCondition(symbol, highTailLength, LEVERAGE)) {
                    logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(highTailLength.toDouble()))
                    return MatchingReport(symbol, OrderSide.SHORT, referenceData)
                }
            } else {
                println("middle low tail, highTail: $highTailLength, lowTail: $lowTailLength, bodyTail: $bodyLength")
                val candleHammerRatio = highTailLength / bodyLength
                if (candleHammerRatio > decimalHammerRatio && getFeeCondition(symbol, lowTailLength, LEVERAGE)) {
                    logger.info("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(lowTailLength.toDouble()))
                    return MatchingReport(symbol, OrderSide.LONG, referenceData)
                }
            }
        }

        return NonMatchingReport
    }

    /*
        MARKET ORDER => taker
        taker fee : 0.045%
     */
    private fun getFeeCondition(symbol: Symbol, estimatedPriceChange: BigDecimal, leverage: Int): Boolean {
        val estimatedEntryPrice = indicatorUseCase.getLastPrice(symbol)
        val estimatedROE = estimatedPriceChange.divide(estimatedEntryPrice)
        return estimatedROE.multiply(BigDecimal(100)) >
                BigDecimal(TAKER_FEE_RATE).multiply(BigDecimal(leverage)).multiply(BigDecimal(FEE_COUNT))
    }
}
