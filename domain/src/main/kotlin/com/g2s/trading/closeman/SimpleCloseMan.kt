package com.g2s.trading.closeman

import com.g2s.trading.indicator.IndicatorUseCase
import com.g2s.trading.lock.LockUsage
import com.g2s.trading.lock.LockUseCase
import com.g2s.trading.order.OrderSide
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.StrategySpec
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class SimpleCloseMan(
    private val positionUseCase: PositionUseCase,
    private val indicatorUseCase: IndicatorUseCase,
    private val lockUseCase: LockUseCase
) : CloseMan {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun type(): String {
        return "simple"
    }

    var cntProfit = 0
    var cntLoss = 0

    override fun close(strategySpec: StrategySpec) {
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

