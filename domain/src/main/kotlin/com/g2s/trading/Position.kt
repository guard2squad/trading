package com.g2s.trading

data class Position(
    val symbol: String,
    val positionAmt: Double,
    val entryPrice: Double,
    val breakEvenPrice: Double,
    val markPrice: Double,
    val unRealizedProfit: Double,
    val liquidationPrice: Double,
    val updateTime: Long,
)

/*
    val orderSide: OrderSide,
    val quantity: String,
    val type: String,
    val positionSide: String = "BOTH", // ONE_WAY_MODE
    val timestamp: String = LocalDateTime.now().toString()
 */