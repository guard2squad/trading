package com.g2s.trading.repository

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.history.HistoryRepository
import com.g2s.trading.history.OpenHistory
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

@Component
class MongoHistoryRepository(
    private val mongoTemplate: MongoTemplate
) : HistoryRepository {
    companion object {
        private const val HISTORY_COLLECTION_NAME = "history"
        private val logger = LoggerFactory.getLogger(MongoHistoryRepository::class.java)
        private val om = ObjectMapperProvider.get()
    }

    override fun saveOpenHistory(history: OpenHistory) {
        mongoTemplate.save(history, HISTORY_COLLECTION_NAME)
    }

    override fun saveCloseHistory(history: CloseHistory) {
        mongoTemplate.save(history, HISTORY_COLLECTION_NAME)
    }

    override fun getAllHistory(strategyKey: String): List<JsonNode> {
        val query = Query()
        query.addCriteria(Criteria.where("strategyKey").`is`(strategyKey))
        val histories = mongoTemplate.find(query, Map::class.java, HISTORY_COLLECTION_NAME)

        return histories.map { result -> om.valueToTree(result) }
    }

    override fun updateOpenHistory(history: OpenHistory) {
        val query = Query.query(Criteria.where("historyKey").`is`(history.historyKey).and("openCondition").exists(true))
        val update = Update().apply {
            set("transactionTime", history.transactionTime)
            set("commission", history.commission)
            set("afterBalance", history.afterBalance)
            set("syncedQuoteQty", history.syncedQuoteQty)
        }
        mongoTemplate.updateFirst(query, update, OpenHistory::class.java, HISTORY_COLLECTION_NAME)
        logger.debug("update open history")
    }

    override fun updateCloseHistory(history: CloseHistory) {
        val query = Query.query(Criteria.where("historyKey").`is`(history.historyKey).and("closeCondition").exists(true))
        val update = Update().apply {
            set("transactionTime", history.transactionTime)
            set("realizedPnL", history.realizedPnL)
            set("commission", history.commission)
            set("afterBalance", history.afterBalance)
            set("syncedQuoteQty", history.syncedQuoteQty)
        }
        mongoTemplate.updateFirst(query, update, CloseHistory::class.java, HISTORY_COLLECTION_NAME)
        logger.debug("update close history")
    }
}
