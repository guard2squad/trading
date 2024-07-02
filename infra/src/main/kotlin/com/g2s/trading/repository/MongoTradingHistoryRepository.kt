package com.g2s.trading.repository

import com.g2s.trading.tradingHistory.TradingHistory
import com.g2s.trading.tradingHistory.TradingHistoryRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

@Component
class MongoTradingHistoryRepository(
    private val mongoTemplate: MongoTemplate
) : TradingHistoryRepository {

    companion object {
        private const val TRADING_HISTORY_COLLECTION_NAME = "trading_history"
    }

    override fun saveTradeHistory(tradingHistory: TradingHistory) {
        mongoTemplate.save(tradingHistory, TRADING_HISTORY_COLLECTION_NAME)
    }
}