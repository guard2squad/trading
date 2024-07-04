package com.g2s.trading.tradingHistory

import org.springframework.stereotype.Repository

@Repository
interface TradingHistoryRepository {
    fun saveTradeHistory(tradingHistory: TradingHistory)
}