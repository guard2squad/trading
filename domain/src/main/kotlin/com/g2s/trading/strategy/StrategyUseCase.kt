package com.g2s.trading.strategy

import com.g2s.trading.position.LiquidationData
import org.springframework.stereotype.Service

@Service
class StrategyUseCase(
    private val strategies: List<Strategy<StrategySpec, LiquidationData>>
) {

    fun getStrategies(): List<Strategy<StrategySpec, LiquidationData>> {
        return strategies
    }

    // 오픈 조건
    // 종료 조건
}
