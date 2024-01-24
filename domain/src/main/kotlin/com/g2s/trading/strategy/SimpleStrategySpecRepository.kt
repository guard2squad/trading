package com.g2s.trading.strategy

import com.g2s.trading.Symbol
import com.g2s.trading.account.Asset
import com.g2s.trading.position.CloseReferenceData
import org.springframework.stereotype.Repository

@Repository
class SimpleStrategySpecRepository : StrategySpecRepository<StrategySpec.SimpleStrategySpec> {

    val repository = listOf(
        StrategySpec.SimpleStrategySpec(
            symbols = listOf<Symbol>(Symbol.BTCUSDT),
            strategyKey = "simple",
            asset = Asset.USDT,
            hammerRatio = 2.0,
            allocatedRatio = 0.25,
            simpleCloseReferenceData = CloseReferenceData.SimpleCloseReferenceData(
                price = 0.0
            )
        )
    )

    override fun findAll(): List<StrategySpec.SimpleStrategySpec> {
        return repository
    }
}
