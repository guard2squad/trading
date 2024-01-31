package com.g2s.trading.repository

import com.g2s.trading.ObjectMapperProvider
import com.g2s.trading.account.Asset
import com.g2s.trading.order.Symbol
import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategySpecRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

@Component
class MongoStrategySpecRepository(
    private val mongoTemplate: MongoTemplate
) : StrategySpecRepository {
    override fun saveStrategySpec(strategySpec: StrategySpec) {
        mongoTemplate.save(strategySpec, strategySpec.strategyKey)
    }

    override fun findStrategySpecByKey(strategyKey: String): StrategySpec {
        val query = Query.query(Criteria.where("strategyKey").`is`(strategyKey))
        val result = mongoTemplate.findOne(query, StrategySpec::class.java, "strategySpec")
            ?: return StrategySpec(
                symbols = listOf(Symbol.BTCUSDT),
                strategyKey = "simple",
                strategyType = "simple",
                asset = Asset.USDT,
                allocatedRatio = 0.25,
                op = ObjectMapperProvider.get().readTree(
                    """
                {
                    "hammerRatio": 1.5,
                    "profitRatio": 0.05
                }
                """.trimIndent()
                ),
                trigger = "0/1 * * * * ?"
            )
        return result
    }

    override fun updateStrategySpec(strategySpec: StrategySpec) {
        val query = Query.query(Criteria.where("strategyKey").`is`(strategySpec.strategyKey))
        val update = Update()
            .set("strategyType", strategySpec.strategyType)
            .set("symbols", strategySpec.symbols)
            .set("asset", strategySpec.asset)
            .set("allocatedRatio", strategySpec.allocatedRatio)
            .set("op", strategySpec.op)
            .set("trigger", strategySpec.trigger)
        mongoTemplate.updateFirst(query, update, StrategySpec::class.java, "strategySpec")
    }

    override fun deleteStrategySpec(strategyKey: String) {
        val query = Query.query(Criteria.where("strategyKey").`is`(strategyKey))
        mongoTemplate.remove(query, StrategySpec::class.java, "strategySpec")
    }
}
