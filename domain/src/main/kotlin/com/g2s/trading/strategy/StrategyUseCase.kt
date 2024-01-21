package com.g2s.trading.strategy

import org.springframework.stereotype.Service

@Service
class StrategyUseCase(
    private val strategies: List<Strategy>
) {

    fun getStrategies(): List<Strategy> {
        return strategies
    }
}