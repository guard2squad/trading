package com.g2s.trading

data class Position(
    val positionAmt: Double,
    val entryPrice: Double,
    val breakEvenPrice: Double,
    val markPrice: Double,
    val unRealizedProfit: Double,
    val liquidationPrice: Double,
    val updateTime: Long,
)