package com.g2s.trading.repository

import com.g2s.trading.ObjectMapperProvider
import com.g2s.trading.account.Asset
import com.g2s.trading.order.Symbol
import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategySpecRepository
import org.springframework.stereotype.Component

@Component
class MongoStrategySpecRepository : StrategySpecRepository {
    override fun findStrategySpecByKey(strategyKey: String): StrategySpec {
        return StrategySpec(
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
    }
}
