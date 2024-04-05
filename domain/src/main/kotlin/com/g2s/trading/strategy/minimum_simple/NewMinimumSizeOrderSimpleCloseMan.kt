package com.g2s.trading.strategy.minimum_simple

import com.g2s.trading.MarkPriceUseCase
import com.g2s.trading.PositionEvent
import com.g2s.trading.StrategyEvent
import com.g2s.trading.TradingEvent
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.history.CloseCondition
import com.g2s.trading.history.ConditionUseCase
import com.g2s.trading.lock.LockUsage
import com.g2s.trading.lock.LockUseCase
import com.g2s.trading.order.OrderSide
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategySpecRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

@Component
class NewMinimumSizeOrderSimpleCloseMan(
    private val lockUseCase: LockUseCase,
    private val positionUseCase: PositionUseCase,
    private val markPriceUseCase: MarkPriceUseCase,
    private val conditionUseCase: ConditionUseCase,
    private val accountUseCase: AccountUseCase,
    private val strategySpecRepository: StrategySpecRepository
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private const val TYPE = "minimum_simple"
    }

    var cntProfit = 0
    var cntLoss = 0

    // simple TYPE의 strategySpec들을 관리
    private val specs: ConcurrentHashMap<String, StrategySpec> =
        strategySpecRepository.findAllServiceStrategySpecByType(TYPE)
            .associateBy { it.strategyKey }
            .let { ConcurrentHashMap(it) }

    // 열린 포지션을 관리
    private val symbolPositionMap: ConcurrentHashMap<Position.PositionKey, Position> =
        positionUseCase.getAllPositions()
            .filter { position -> specs.keys.contains(position.strategyKey) }
            .associateBy { it.positionKey }
            .let { ConcurrentHashMap(it) }

    @EventListener
    fun handleStartStrategyEvent(event: StrategyEvent.StartStrategyEvent) {
        val spec = event.source
        if (spec.strategyType != TYPE) return
        specs.putIfAbsent(spec.strategyKey, spec)
    }

    @EventListener
    fun handleUpdateStrategyEvent(event: StrategyEvent.UpdateStrategyEvent) {
        val spec = event.source
        if (spec.strategyType != TYPE) return
        specs.replace(spec.strategyKey, spec)
    }

    @EventListener
    fun handleStopStrategyEvent(event: StrategyEvent.StopStrategyEvent) {
        val spec = event.source
        if (spec.strategyType != TYPE) return
        specs.remove(spec.strategyKey)
    }

    @EventListener
    fun handlePositionSyncedEvent(event: PositionEvent.PositionSyncedEvent) {
        val newPosition = event.source
        if (!specs.keys.contains(newPosition.strategyKey)) return
        symbolPositionMap.compute(newPosition.positionKey) { _, _ ->
            newPosition
        }
        logger.debug("handlePositionSyncedEvent : position strategy key = ${newPosition.strategyKey}, symbol : ${newPosition.symbol.value}")
        logger.debug("after sync positoion symbolPositionMap's mapsize is ${symbolPositionMap.size}")
    }

    @EventListener
    fun handleMarkPriceEvent(event: TradingEvent.MarkPriceRefreshEvent) {
        // find matching position
        val position = symbolPositionMap.asSequence()
            .map { it.value }
            .find { position -> position.symbol == event.source.symbol } ?: return
        // position must be synced
        if (!position.synced) {
            logger.debug("position not synced : ${position.symbol.value}")
            return
        }
        // if you find position, close it
        val acquired = lockUseCase.acquire(position.strategyKey, LockUsage.CLOSE)
        if (!acquired) {
            logger.debug("lock is not acquired. strategyKey : ${position.strategyKey}, symbol : ${position.symbol}")
            return
        }
        // check should close
        val availableBalance = accountUseCase.getAvailableBalance(position.asset)
        val entryPrice = BigDecimal(position.entryPrice)
        val lastPrice = BigDecimal(markPriceUseCase.getMarkPrice(position.symbol).price)
        val spec = specs[position.strategyKey]!!
        val stopLossFactor = BigDecimal(spec.op["stopLossFactor"].asDouble())
        val scale = BigDecimal(spec.op["scale"].asDouble())
        var shouldClose = false
        when (position.orderSide) {
            OrderSide.LONG -> {
                // 손절
                // 운영 scale 반영 전 tailLength
                val tailLength = BigDecimal(position.referenceData["tailLength"].asDouble()).divide(scale)
                if (tailLength.multiply(stopLossFactor) < entryPrice.minus(lastPrice)) {
                    logger.debug(
                        "[롱 손절] entryPrice : $entryPrice | lastPrice: $lastPrice " +
                        "| 오픈 시 꼬리길이(tailLength): $tailLength" +
                        "| StopLossFactor: $stopLossFactor | StopLossFactor 적용한 꼬리길이 : ${tailLength.multiply(stopLossFactor)}" +
                        "| tailLength * stopLossFactor < entryPrice - lastPrice : ${tailLength.multiply(stopLossFactor) < entryPrice.minus(lastPrice)}" +
                        "| specKey : ${spec.strategyKey}"
                    )
                    shouldClose = true
                    conditionUseCase.setCloseCondition(position, CloseCondition.SimpleCondition(
                        tailLength = tailLength,
                        tailLengthWithStopLossFactor = tailLength.multiply(stopLossFactor),
                        entryPrice = entryPrice,
                        lastPrice = lastPrice,
                        beforeBalance =availableBalance.toDouble()
                    ))
                    cntLoss++
                }
                // 익절
                if (lastPrice > entryPrice.plus(BigDecimal(position.referenceData["tailLength"].asDouble()))) {
                    logger.debug(
                        "[롱 익절] entryPrice: $entryPrice | lastPrice: $lastPrice " +
                        "| 오픈 시 scale 반영된 꼬리길이: ${BigDecimal(position.referenceData["tailLength"].asDouble())} " +
                        "| scale: $scale" +
                        "| specKey : ${spec.strategyKey}"
                    )
                    shouldClose = true
                    conditionUseCase.setCloseCondition(position, CloseCondition.SimpleCondition(
                        tailLength = tailLength,
                        tailLengthWithStopLossFactor = tailLength.multiply(stopLossFactor),
                        entryPrice = entryPrice,
                        lastPrice = lastPrice,
                        beforeBalance =availableBalance.toDouble()
                    ))
                    cntProfit++
                }
                logger.debug(
                    "[롱 청산] entryPrice: $entryPrice | lastPrice: $lastPrice | 오픈 시 꼬리길이: $tailLength" +
                    "| 롱 손절? : ${tailLength < entryPrice.minus(lastPrice)} " +
                    "| 롱 익절? : ${lastPrice > entryPrice.plus(BigDecimal(position.referenceData["tailLength"].asDouble()))}"
                )
            }

            OrderSide.SHORT -> {
                // 손절
                // 운영 scale 반영 전 tailLength
                val tailLength = BigDecimal(position.referenceData["tailLength"].asDouble()).divide(scale)
                if (tailLength.multiply(stopLossFactor) < lastPrice.minus(entryPrice)) {
                    logger.debug(
                        "[숏 손절] entryPrice : $entryPrice | lastPrice: $lastPrice " +
                                "| 오픈 시 꼬리길이(tailLength): $tailLength" +
                                "| StopLossFactor: $stopLossFactor | StopLossFactor 적용한 꼬리길이 : ${tailLength.multiply(stopLossFactor)}" +
                                "| tailLength * stopLossFactor < lastPrice - entryPrice : ${tailLength.multiply(stopLossFactor) < entryPrice.minus(lastPrice)}" +
                                "| specKey : ${spec.strategyKey}"
                    )
                    shouldClose = true
                    conditionUseCase.setCloseCondition(position, CloseCondition.SimpleCondition(
                        tailLength = tailLength,
                        tailLengthWithStopLossFactor = tailLength.multiply(stopLossFactor),
                        entryPrice = entryPrice,
                        lastPrice = lastPrice,
                        beforeBalance =availableBalance.toDouble()
                    ))
                    cntLoss++
                }
                // 익절
                if (lastPrice < entryPrice.minus(BigDecimal(position.referenceData["tailLength"].asDouble()))) {
                    logger.debug(
                        "[숏 익절] entryPrice: $entryPrice | lastPrice: $lastPrice " +
                                "| 오픈 시 scale 반영된 꼬리길이: ${BigDecimal(position.referenceData["tailLength"].asDouble())} " +
                                "| scale: $scale" +
                                "| specKey : ${spec.strategyKey}"
                    )
                    shouldClose = true
                    conditionUseCase.setCloseCondition(position, CloseCondition.SimpleCondition(
                        tailLength = tailLength,
                        tailLengthWithStopLossFactor = tailLength.multiply(stopLossFactor),
                        entryPrice = entryPrice,
                        lastPrice = lastPrice,
                        beforeBalance =availableBalance.toDouble()
                    ))
                    cntProfit++
                }
                logger.debug(
                    "[숏 청산] entryPrice: $entryPrice | lastPrice: $lastPrice | 오픈 시 꼬리길이: $tailLength" +
                            "| 숏 손절? : ${tailLength < lastPrice.minus(entryPrice)} " +
                            "| 숏 익절? : ${lastPrice < entryPrice.minus(BigDecimal(position.referenceData["tailLength"].asDouble()))}"
                )
            }
        }
        // close position
        if (shouldClose) {
            logger.debug("포지션 청산: $position")
            logger.debug("익절: $cntProfit, 손절: $cntLoss")
            symbolPositionMap.remove(position.positionKey)
            positionUseCase.closePosition(position, spec)
        }
        logger.debug("포지션 청산 실패: $position")
        logger.debug("${position.symbol} shouldClose: $shouldClose")
        logger.debug("symbolPositionMap의 크기 : " + symbolPositionMap.size.toString())
        // 릴리즈
        lockUseCase.release(position.strategyKey, LockUsage.CLOSE)
    }
}
