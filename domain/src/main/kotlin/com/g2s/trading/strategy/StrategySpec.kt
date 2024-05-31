package com.g2s.trading.strategy

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.account.Asset

data class StrategySpec(
    val strategyKey: String,
    val strategyType: StrategyType,
    val symbols: List<String>,
    val asset: Asset,
    val allocatedRatio: Double,
    val op: JsonNode,
    val status: StrategySpecServiceStatus
)