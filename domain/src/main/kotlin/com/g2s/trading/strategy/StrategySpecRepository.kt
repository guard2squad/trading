package com.g2s.trading.strategy

import org.springframework.stereotype.Repository

@Repository
interface StrategySpecRepository {
    fun saveStrategySpec(strategySpec: StrategySpec)
    fun findStrategySpecByKey(strategyKey: String): StrategySpec
    fun updateStrategySpec(strategySpec: StrategySpec)
    fun deleteStrategySpec(strategyKey: String)
}
