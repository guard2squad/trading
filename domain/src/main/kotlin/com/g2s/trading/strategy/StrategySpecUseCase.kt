package com.g2s.trading.strategy

import org.springframework.stereotype.Service

@Service
class StrategySpecUseCase(
    private val strategySpecRepository: StrategySpecRepository,
) {
    // 전략 - 스펙 목록
    private val specsByStrategy: MutableMap<StrategyType, MutableList<StrategySpec>> = mutableMapOf()

    init {
        load()
    }

    private fun load() {
        val serviceSpecs = strategySpecRepository.findAllServiceStrategySpec()
        serviceSpecs.forEach { spec ->
            specsByStrategy.putIfAbsent(spec.strategyType, mutableListOf())
            specsByStrategy[spec.strategyType]!!.add(spec)
        }
    }

    fun findAllServiceSpecs(): List<StrategySpec> {
        return strategySpecRepository.findAllServiceStrategySpec()
    }

    fun findSpecsByStrategyType(strategyType: StrategyType): List<StrategySpec>? {
        return specsByStrategy[strategyType]
    }
}