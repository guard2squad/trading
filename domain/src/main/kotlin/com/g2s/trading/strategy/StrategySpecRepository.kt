package com.g2s.trading.strategy

interface StrategySpecRepository<T : StrategySpec> {
    fun findAll(): List<T>
}
