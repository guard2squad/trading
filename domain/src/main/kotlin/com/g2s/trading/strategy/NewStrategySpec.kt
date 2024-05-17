package com.g2s.trading.strategy

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.account.Asset

data class NewStrategySpec (
    val strategyKey: String,
    val strategyType: NewStrategyType,
    val symbols: List<String>,
    val asset: Asset,
    val allocatedRatio: Double,
    val op: JsonNode,
    val trigger: String,
    val status: StrategySpecServiceStatus
)