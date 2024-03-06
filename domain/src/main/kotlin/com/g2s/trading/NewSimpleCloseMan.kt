package com.g2s.trading

import com.g2s.trading.lock.LockUsage
import com.g2s.trading.lock.LockUseCase
import com.g2s.trading.order.OrderSide
import com.g2s.trading.symbol.Symbol
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

    // simple TYPE의 strategySpec들을 관리
    private val specs: ConcurrentHashMap<String, StrategySpec> =
        strategySpecRepository.findAllServiceStrategySpecByType(TYPE)
            .associateBy { it.strategyKey }
            .let { ConcurrentHashMap(it) }

    // 열린 포지션을 관리
    private val symbolPositionMap: ConcurrentHashMap<Symbol, Position> =
        positionUseCase.getAllLoadedPosition()
            .filter { position -> specs.keys.contains(position.strategyKey) }
            .associateBy { it.symbol }
            .let { ConcurrentHashMap(it) }

    @EventListener
    fun handleStartStrategyEvent(event: StrategyEvent.StartStrategyEvent) {
        val spec = event.source
        if (spec.strategyType != TYPE) return
        specs[event.source.strategyKey] = event.source
    }

    @EventListener
    fun handleUpdateStrategyEvent(event: StrategyEvent.UpdateStrategyEvent) {
        val spec = event.source
        if (spec.strategyType != TYPE) return
        specs.replace(event.source.strategyKey, event.source)
    }

    @EventListener
    fun handleStopStrategyEvent(event: StrategyEvent.StopStrategyEvent) {
        val spec = event.source
        if (spec.strategyType != TYPE) return
        specs.remove(event.source.strategyKey)
    }

    @EventListener
    fun handlePositionSyncedEvent(event: PositionEvent.PositionSyncedEvent) {
        val newPosition = event.source
        logger.debug("handlePositionSyncedEvent : position strategy key = ${newPosition.strategyKey}")
        if (!specs.keys.contains(newPosition.strategyKey)) return
        logger.debug("handlePositionSyncedEvent: ${newPosition.symbol}")
        symbolPositionMap.replace(newPosition.symbol, newPosition)
        logger.debug("position update for key: ${newPosition.strategyKey}")
    }

    @EventListener
    fun handleMarkPriceEvent(event: TradingEvent.MarkPriceRefreshEvent) {
        // find matching position
        val position = symbolPositionMap.asSequence()
            .map { it.value }
            .find { position -> position.symbol == event.source.symbol } ?: return
        // position must be synced
        if (!position.synced) {
            return
        }
        // if you find position, close it
        val acquired = lockUseCase.acquire(position.strategyKey, LockUsage.CLOSE)
        if (!acquired) return
        // check should close
        val entryPrice = BigDecimal(position.entryPrice)
        val lastPrice = BigDecimal(markPriceUseCase.getMarkPrice(position.symbol).price)
        val spec = specs[position.strategyKey]!!
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
            symbolPositionMap.remove(position.symbol)
            logger.debug("position deleted from symbolPositionMap: ${position.strategyKey}")
            positionUseCase.closePosition(position, spec)
        }
        // 릴리즈
        lockUseCase.release(position.strategyKey, LockUsage.CLOSE)
    }
}
