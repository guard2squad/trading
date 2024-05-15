package com.g2s.trading.strategy

import org.springframework.stereotype.Service

@Service
class NewStrategySpecUseCase(
    private val strategySpecRepository: StrategySpecRepository,
) {
    // 전략 - 스펙 목록
    private val specsByStrategy: MutableMap<NewStrategyType, MutableList<NewStrategySpec>> = mutableMapOf()

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

    fun findAllServiceSpecs(): List<NewStrategySpec> {
        return strategySpecRepository.findAllServiceStrategySpec()
    }

    fun findSpecsByStrategyType(strategyType: NewStrategyType): List<NewStrategySpec>? {
        return specsByStrategy[strategyType]
    }
}