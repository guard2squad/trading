package com.g2s.trading.history

import com.fasterxml.jackson.databind.JsonNode

interface HistoryRepository {
    fun saveHistory(history: History)
    fun getAllHistory(strategyKey: String): List<JsonNode>
    fun updateOpenHistory(history: History.Open)
    fun updateCloseHistory(history: History.Close)
}
