package com.g2s.trading.strategy

import org.springframework.stereotype.Repository

@Repository
interface StrategySpecRepository {
    fun findStrategySpecByKey(strategyKey: String): NewStrategySpec?
    fun findAllServiceStrategySpec(): List<NewStrategySpec>
    fun updateSpec(strategySpec: NewStrategySpec): NewStrategySpec
}
