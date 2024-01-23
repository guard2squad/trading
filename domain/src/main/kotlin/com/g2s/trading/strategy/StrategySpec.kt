package com.g2s.trading.strategy

import com.g2s.trading.Symbol
import com.g2s.trading.account.Asset

sealed class StrategySpec {

    data class SimpleStrategySpec(
        val symbols : List<Symbol>,
        val strategyKey : String, // simple
        val asset : Asset,
        val hammerRatio: Double,
        val allocatedRatio: Double
    ) : StrategySpec()

}
