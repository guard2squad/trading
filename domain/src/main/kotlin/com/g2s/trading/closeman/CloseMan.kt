package com.g2s.trading.closeman

import com.g2s.trading.strategy.StrategySpec

interface CloseMan {
    fun type(): String
    fun close(strategySpec: StrategySpec)
}
