package com.g2s.trading.strategy

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.order.Symbol
import com.g2s.trading.account.Asset

data class StrategySpec (
    val symbols: List<Symbol>,
    val strategyKey: String,
    val asset: Asset,
    val allocatedRatio: Double,
    val op: JsonNode
)
