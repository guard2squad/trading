package com.g2s.trading.openman

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.Symbol

sealed class AnalyzeReport {
    data class MatchingReport(
        val symbol: Symbol,
        val orderSide: OrderSide,
        val referenceData : JsonNode
    ) : AnalyzeReport()

    object NonmatchingReport : AnalyzeReport()
}
