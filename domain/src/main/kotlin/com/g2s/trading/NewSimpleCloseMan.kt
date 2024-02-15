package com.g2s.trading

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

    private val strategyPositionMap = ConcurrentHashMap<String, Position>()
    private val specs: ConcurrentHashMap<String, StrategySpec> = ConcurrentHashMap<String, StrategySpec>().also { map ->
        map.putAll(
            strategySpecRepository.findAllServiceStrategySpecByType(TYPE)
                .associateBy { it.strategyKey })
    }

    @EventListener
    fun handleStartStrategyEvent(event: StrategyEvent.StartStrategyEvent) {
        specs.computeIfAbsent(event.source.strategyKey) {
            event.source
        }
    }

    @EventListener
    fun handleUpdateStrategyEvent(event: StrategyEvent.UpdateStrategyEvent) {
        specs.replace(event.source.strategyKey, event.source)
    }

    @EventListener
    fun handleStopStrategyEvent(event: StrategyEvent.StopStrategyEvent) {
        specs.remove(event.source.strategyKey)
    }

    @EventListener
    fun handleOpenPositionEvent(event: PositionEvent.PositionOpenEvent) {
        strategyPositionMap[event.source.strategyKey] = event.source
    }

    @EventListener
    fun handleMarkPriceEvent(event: TradingEvent.MarkPriceRefreshEvent) {
        val position = strategyPositionMap.asSequence()
            .map { it.value }
            .find { it.symbol == event.source.symbol } ?: return
        // if you find position, close
        lockUseCase.acquire(position.strategyKey, LockUsage.CLOSE)
        // check should close
        val entryPrice = BigDecimal(position.entryPrice)
        val lastPrice = BigDecimal(markPriceUseCase.getMarkPrice(position.symbol).price)
        var shouldClose = false
        when (position.orderSide) {
            OrderSide.LONG -> {
                // 손절
                if (BigDecimal(position.referenceData["low"].asDouble()) > lastPrice) {
                    logger.info(
                        "롱 손절: lastPrice: $lastPrice, 오픈시 꼬리 최저값: ${position.referenceData["low"].asDouble()}"
                    )
                    shouldClose = true
                    cntLoss++
                }
                // 익절
                if (lastPrice > entryPrice.plus(BigDecimal(position.referenceData["tailLength"].asDouble()))) {
                    logger.info(
                        "롱 익절: lastPrice: $lastPrice, entryPrice: $entryPrice, 오픈시 꼬리 길이: ${position.referenceData["tailLength"].asDouble()}"
                    )
                    shouldClose = true
                    cntProfit++
                }
            }

            OrderSide.SHORT -> {
                // 손절
                if (BigDecimal(position.referenceData["high"].asDouble()) < lastPrice) {
                    logger.info(
                        "숏 손절: lastPrice: $lastPrice, 오픈시 꼬리 최대값: ${position.referenceData["high"].asDouble()}"
                    )
                    shouldClose = true
                    cntLoss++
                }
                // 익절
                if (lastPrice < entryPrice.minus(BigDecimal(position.referenceData["tailLength"].asDouble()))) {
                    logger.info(
                        "숏 익절: lastPrice: $lastPrice, entryPrice: $entryPrice, 오픈시 꼬리 길이: ${position.referenceData["tailLength"].asDouble()}"
                    )
                    shouldClose = true
                    cntProfit++
                }
            }
        }
        // close position
        if (shouldClose) {
            logger.info("포지션 청산: $position")
            logger.info("익절: $cntProfit, 손절: $cntLoss")
            positionUseCase.closePosition(position)
        }
        // 릴리즈
        lockUseCase.release(position.strategyKey, LockUsage.CLOSE)
    }
}
