package com.g2s.trading.repository

import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategySpecRepository
import com.g2s.trading.strategy.StrategySpecServiceStatus
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

@Component
class MongoStrategySpecRepository(
    private val mongoTemplate: MongoTemplate
) : StrategySpecRepository {

    companion object {
        private const val SPEC_COLLECTION_NAME = "simple"
    }

    override fun findStrategySpecByKey(strategyKey: String): StrategySpec? {
        val query = Query.query(Criteria.where("strategyKey").`is`(strategyKey))
        val result = mongoTemplate.findOne(query, StrategySpec::class.java, SPEC_COLLECTION_NAME)

        return result
    }

    override fun findAllServiceStrategySpec(): List<StrategySpec> {
        val query = Query.query(
            Criteria.where(StrategySpec::status.name).`is`(StrategySpecServiceStatus.SERVICE.name)
        )
        return mongoTemplate.find(
            query, StrategySpec::class.java, SPEC_COLLECTION_NAME
        )
    }
}
