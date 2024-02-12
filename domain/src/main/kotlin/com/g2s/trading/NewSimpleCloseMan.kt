package com.g2s.trading

import com.g2s.trading.lock.LockUsage
import com.g2s.trading.lock.LockUseCase
import com.g2s.trading.order.OrderSide
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
    private val strategySpecRepository: StrategySpecRepository
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private const val TYPE = "simple"
    }

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
        // 포지션 유스케이스에서 오픈하고 이벤트 퍼블리싱
        // position(전략 키)
        // 여기서 포지션, 전략키 가지고
    }

    @EventListener
    fun handleMarkPriceEvent(event: TradingEvent.MarkPriceRefreshEvent) {
        // Markprice(symbol, 값)
        // MarkPrice의 심볼로 포지션 조회? 만들어야함
        // 포지션 있으면 락
        // sholud close 체크
        // 트루면 클로즈 포지션
        // 릴리즈
    }

    fun close(strategySpec: StrategySpec) {
        // lock
        val acquired = lockUseCase.acquire(strategySpec.strategyKey, LockUsage.CLOSE)
        if (!acquired) return

        val position = positionUseCase.getPosition(strategySpec.strategyKey) ?: run {
            lockUseCase.release(strategySpec.strategyKey, LockUsage.CLOSE)
            return
        }
        val lastPrice = indicatorUseCase.getLastPrice(position.symbol)
        val entryPrice = BigDecimal(position.entryPrice)

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

        if (shouldClose) {
            logger.info("포지션 청산: $position")
            logger.info("익절: $cntProfit, 손절: $cntLoss")
            positionUseCase.closePosition(position)
            positionUseCase.removePosition(strategySpec.strategyKey)
        }

        lockUseCase.release(strategySpec.strategyKey, LockUsage.CLOSE)
    }

    // 수수료 조건 계산
    private fun getFeeCondition(lastPrice: BigDecimal, entryPrice: BigDecimal) {
        val roe = (lastPrice - entryPrice).divide(entryPrice).multiply(BigDecimal(100))
        // TODO(taker vs maker check)

    }
}