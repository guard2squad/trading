package com.g2s.trading.openman

import com.g2s.trading.strategy.StrategySpec

interface OpenMan {
    fun open(strategySpec: StrategySpec)
}
