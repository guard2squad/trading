package com.g2s.trading.openman

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.g2s.trading.ObjectMapperProvider
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.indicator.IndicatorUseCase
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.openman.AnalyzeReport.MatchingReport
import com.g2s.trading.openman.AnalyzeReport.NonmatchingReport
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
    private val indicatorUseCase: IndicatorUseCase
) : OpenMan {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    companion object {
        private val MAXIMUM_HAMMER_RATIO = BigDecimal(9999)
    }
    override fun type() = "simple"

    override fun open(strategySpec: StrategySpec) {
        // 이미 포지션 있는지 확인
        if (positionUseCase.hasPosition(strategySpec.strategyKey)) return
        logger.debug("no position")

        // spec에 운영된 symbol중에서 현재 포지션이 없는 symbol 확인
        val unUsedSymbols = strategySpec.symbols - positionUseCase.getAllUsedSymbols()
        if (unUsedSymbols.isEmpty()) return

        // 계좌 잔고 확인
        accountUseCase.syncAccount()
        val allocatedBalance =
            accountUseCase.getAllocatedBalancePerStrategy(strategySpec.asset, strategySpec.allocatedRatio)
        val availableBalance = accountUseCase.getAvailableBalance(strategySpec.asset)
        logger.debug("allocated: $allocatedBalance, available: $availableBalance")
        if (allocatedBalance > availableBalance) {
            logger.debug("not enough money")
            return
        }

        // 오픈 가능한 symbol 확인
        val hammerRatio = strategySpec.op["hammerRatio"].asDouble()
        val analyzeReport = unUsedSymbols.asSequence()
            .map { unUsedSymbol -> analyze(unUsedSymbol, hammerRatio) }
            .filterIsInstance<MatchingReport>()
            .take(1)
            .firstOrNull() ?: return
        logger.debug("analyzeReport: $analyzeReport")

        val lastPrice = indicatorUseCase.getLastPrice(analyzeReport.symbol)
        val order = orderUseCase.createOrder(
            strategySpec.strategyKey,
            lastPrice,
            allocatedBalance,
            analyzeReport.symbol,
            analyzeReport.orderSide,
            OrderType.MARKET
        )
        logger.debug("order: $order, lastPrice: $lastPrice")

        val position = orderUseCase.openOrder(order)
            .apply {
                orderSide = order.orderSide
                orderType = order.orderType
                referenceData = analyzeReport.referenceData
            }
        logger.debug("position: $position")
        positionUseCase.addPosition(strategySpec.strategyKey, position)
    }

    private fun analyze(symbol: Symbol, hammerRatio: Double): AnalyzeReport {
        val candleSticks = indicatorUseCase.getCandleStick(symbol, Interval.ONE_MINUTE, 2)
        val lastCandleStick = candleSticks.first()

        val tailTop = BigDecimal(lastCandleStick.high)
        val tailBottom = BigDecimal(lastCandleStick.low)
        val bodyTop = BigDecimal(max(lastCandleStick.open, lastCandleStick.close))
        val bodyBottom = BigDecimal(min(lastCandleStick.open, lastCandleStick.close))
        val bodyLength = bodyTop - bodyBottom // TODO: 리얼에서는 제한 두기
        val decimalHammerRatio = BigDecimal(hammerRatio)

        println("lastCandleStick: $lastCandleStick")

        if (bodyLength == BigDecimal.ZERO) {
            println("star candle")
            val topTailLength = tailTop - bodyTop
            val bottomTailLength = bodyTop - tailBottom
            if(topTailLength > bottomTailLength ) {
                val candleHammerRatio = if(bottomTailLength != BigDecimal.ZERO) topTailLength / bottomTailLength else MAXIMUM_HAMMER_RATIO
                if(candleHammerRatio > decimalHammerRatio) {
                    logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(topTailLength.toDouble()))
                    return MatchingReport(symbol, OrderSide.SHORT, referenceData)
                }
            } else {
                val candleHammerRatio = if(topTailLength != BigDecimal.ZERO) bottomTailLength / topTailLength else MAXIMUM_HAMMER_RATIO
                if(candleHammerRatio > decimalHammerRatio) {
                    logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(bottomTailLength.toDouble()))
                    return MatchingReport(symbol, OrderSide.LONG, referenceData)
                }
            }
        } else if (tailTop > bodyTop && tailBottom == bodyBottom) {
            println("top tail")
            val tailLength = tailTop - bodyTop
            val candleHammerRatio = tailLength / bodyLength
            if (candleHammerRatio > decimalHammerRatio) {
                logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                val referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
                (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(tailLength.toDouble()))
                return MatchingReport(symbol, OrderSide.SHORT, referenceData)
            }
        } else if (tailBottom < bodyBottom && tailTop == bodyTop) {
            println("bottom tail")
            val tailLength = bodyBottom - tailBottom
            val candleHammerRatio = tailLength / bodyLength
            if (candleHammerRatio > decimalHammerRatio) {
                logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
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
                if (candleHammerRatio > decimalHammerRatio) {
                    logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(highTailLength.toDouble()))
                    return MatchingReport(symbol, OrderSide.SHORT, referenceData)
                }
            } else {
                println("middle low tail, highTail: $highTailLength, lowTail: $lowTailLength, bodyTail: $bodyLength")
                val candleHammerRatio = highTailLength / bodyLength
                if (candleHammerRatio > decimalHammerRatio) {
                    logger.debug("candleHammer: ${candleHammerRatio.toDouble()}, decimalHammer: ${decimalHammerRatio.toDouble()}")
                    val referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
                    (referenceData as ObjectNode).set<DoubleNode>("tailLength", DoubleNode(lowTailLength.toDouble()))
                    return MatchingReport(symbol, OrderSide.LONG, referenceData)
                }
            }
        }

        return NonmatchingReport
    }
}
