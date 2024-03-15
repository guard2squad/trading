package com.g2s.trading.history

import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.symbol.Symbol
import kotlin.reflect.jvm.internal.impl.incremental.components.Position

data class History(
    val symbol: Symbol,
    val historySide: HistorySide,
    val averagePrice: Double,
    val quantity: Double,
    val fee: Double,
    val realizedProfit: Double,
    val orderTime : Long,
    var position: Position? = null,
    var strategySpec: StrategySpec? = null,
)
