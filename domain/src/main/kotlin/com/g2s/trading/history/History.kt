package com.g2s.trading.history

import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.symbol.Symbol

data class History(
    val symbol: Symbol,
    val historySide: HistorySide,
    val averagePrice: Double,
    val quantity: Double,
    val fee: Double,
    val realizedProfit: Double,
    val orderTime : Long,
    var strategySpec: StrategySpec? = null
)
