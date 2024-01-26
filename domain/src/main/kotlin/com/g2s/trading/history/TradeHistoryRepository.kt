package com.g2s.trading.history

import org.springframework.stereotype.Repository

@Repository
interface TradeHistoryRepository {
    fun saveTradeHistory(history: TradeHistory)
}
