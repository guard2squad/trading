package com.g2s.trading.tradingHistory

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.order.OrderSide

data class TradingHistory(
    val symbol: String,
    val side: OrderSide,
    val quantity: Double,
    val strategyKey: String,
    val candlestickPattern: String,
    val expectedEntryPrice: Double,
    val entryPrice: Double,
    val takeProfitPrice: Double,
    val stopLossPrice: Double,
    val closePrice: Double,
    val expectedFee: Double,
    val fee: Double,
    val pnl: Double,
    var result: TradingResult = TradingResult.BREAK_EVEN,
    val closeTime: Long,
    val referenceData: JsonNode,
) {
    init {
        result = when {
            pnl > 0 -> TradingResult.PROFIT
            pnl < 0 -> TradingResult.LOSS
            else -> TradingResult.BREAK_EVEN
        }
    }
}
