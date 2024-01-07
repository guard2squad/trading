package com.g2s.trading

import org.springframework.stereotype.Repository

@Repository
interface TradeHistoryRepository {
    fun saveTradeHistory(history: TradeHistory)
}