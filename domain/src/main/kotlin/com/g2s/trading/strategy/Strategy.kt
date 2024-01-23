package com.g2s.trading.strategy

interface Strategy<T : StrategySpec> {
    fun setSpec(strategySpec: T)
    fun invoke() : StrategyResult?
}
