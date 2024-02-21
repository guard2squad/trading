package com.g2s.trading

import com.g2s.trading.lock.LockUsage
import com.g2s.trading.lock.LockUseCase
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.Symbol
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
class NewSimpleCloseMan(
    private val lockUseCase: LockUseCase,
    private val positionUseCase: PositionUseCase,
    private val markPriceUseCase: MarkPriceUseCase,
    private val strategySpecRepository: StrategySpecRepository
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private const val TYPE = "simple"
    }

    var cntProfit = 0
    var cntLoss = 0

    private val strategyPositionMap: ConcurrentHashMap<String, Pair<StrategySpec, Position?>> =
        strategySpecRepository.findAllServiceStrategySpecByType(TYPE)
            .associate { it.strategyKey to Pair(it, null) }
            .let { ConcurrentHashMap(it) }

    init {
        logger.debug("NewSimpleCloseMan init")
        val positions = positionUseCase.getAllLoadedPosition()
        positions.forEach { position ->
            strategyPositionMap[position.strategyKey]?.let { pair ->
                strategyPositionMap[position.strategyKey] = pair.copy(second = position)
            }
        }
    }

    @EventListener
    fun handleStartStrategyEvent(event: StrategyEvent.StartStrategyEvent) {
        strategyPositionMap.putIfAbsent(event.source.strategyKey, Pair(event.source, null))
    }

    @EventListener
    fun handleUpdateStrategyEvent(event: StrategyEvent.UpdateStrategyEvent) {
        val spec = event.source
        val position = strategyPositionMap[spec.strategyKey]?.second
        strategyPositionMap.replace(spec.strategyKey, Pair(spec, position))
    }

    @EventListener
    fun handleStopStrategyEvent(event: StrategyEvent.StopStrategyEvent) {
        strategyPositionMap.remove(event.source.strategyKey)
    }

    @EventListener
    fun handlePositionSyncedEvent(event: PositionEvent.PositionSyncedEvent) {
        val newPosition = event.source
        logger.debug("handlePositionOpenedEvent: ${newPosition.symbol}")
        strategyPositionMap.computeIfPresent(newPosition.strategyKey) { _, pair ->
            logger.debug("position update for key: ${newPosition.strategyKey}")
            pair.copy(second = newPosition)
        }
    }

    @EventListener
    fun handleMarkPriceEvent(event: TradingEvent.MarkPriceRefreshEvent) {
        logger.debug("handleMarkPriceEvent: ${event.source.symbol}")
        // find matching position
        val position = strategyPositionMap.asSequence()
            .map { it.value.second }
            .filterNotNull()
            .find { it.symbol == event.source.symbol } ?: return
        // position must be synced
        if (!position.synced) {
            return
        }
        // if you find position, close it
        lockUseCase.acquire(position.strategyKey, LockUsage.CLOSE)
        // check should close
        val entryPrice = BigDecimal(position.entryPrice)
        val lastPrice = BigDecimal(markPriceUseCase.getMarkPrice(position.symbol).price)
        val spec = strategyPositionMap[position.strategyKey]!!.first
        val stopLossFactor = BigDecimal(spec.op["stopLossFactor"].asDouble())
        var shouldClose = false
        when (position.orderSide) {
            OrderSide.LONG -> {
                // 손절
                if (BigDecimal(position.referenceData["low"].asDouble()).multiply(stopLossFactor) > lastPrice) {
                    logger.debug(
                        "롱 손절: lastPrice: $lastPrice, 오픈시 꼬리 최저값: ${position.referenceData["low"].asDouble()}" +
                                ", StopLossFactor 반영 후 꼬리 최저값: ${
                                    BigDecimal(position.referenceData["low"].asDouble()).multiply(
                                        stopLossFactor
                                    )
                                }"
                    )
                    shouldClose = true
                    cntLoss++
                }
                // 익절
                if (lastPrice > entryPrice.plus(BigDecimal(position.referenceData["tailLength"].asDouble()))) {
                    logger.debug(
                        "롱 익절: lastPrice: $lastPrice, entryPrice: $entryPrice, 오픈시 꼬리 길이: ${position.referenceData["tailLength"].asDouble()}"
                    )
                    shouldClose = true
                    cntProfit++
                }
            }

            OrderSide.SHORT -> {
                // 손절
                if (BigDecimal(position.referenceData["high"].asDouble()).multiply(stopLossFactor) < lastPrice) {
                    logger.debug(
                        "숏 손절: lastPrice: $lastPrice, 오픈시 꼬리 최대값: ${position.referenceData["high"].asDouble()}" +
                                ", StopLossFactor 반영 후 꼬리 최저값: ${
                                    BigDecimal(position.referenceData["high"].asDouble()).multiply(
                                        stopLossFactor
                                    )
                                }"
                    )
                    shouldClose = true
                    cntLoss++
                }
                // 익절
                if (lastPrice < entryPrice.minus(BigDecimal(position.referenceData["tailLength"].asDouble()))) {
                    logger.debug(
                        "숏 익절: lastPrice: $lastPrice, entryPrice: $entryPrice, 오픈시 꼬리 길이: ${position.referenceData["tailLength"].asDouble()}"
                    )
                    shouldClose = true
                    cntProfit++
                }
            }
        }
        // close position
        if (shouldClose) {
            logger.debug("포지션 청산: $position")
            logger.debug("익절: $cntProfit, 손절: $cntLoss")
            positionUseCase.closePosition(position)
        }
        // 릴리즈
        lockUseCase.release(position.strategyKey, LockUsage.CLOSE)
    }

    fun testHandleMarkPriceEvent(symbol: Symbol) {
        val position = strategyPositionMap.asSequence()
            .map { it.value.second }
            .filterNotNull()
            .find { it.symbol == symbol } ?: return

        positionUseCase.closePosition(position)
    }
}
