package com.g2s.trading.history

import com.fasterxml.jackson.databind.JsonNode

interface HistoryRepository {
    fun saveOpenHistory(history: OpenHistory)
    fun saveCloseHistory(history: CloseHistory)
    fun getAllHistory(strategyKey: String): List<JsonNode>
    fun updateOpenHistory(history: OpenHistory)
    fun updateCloseHistory(history: CloseHistory)
}
