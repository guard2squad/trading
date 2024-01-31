package com.g2s.trading

import com.g2s.trading.account.Asset
import com.g2s.trading.closeman.CloseMan
import com.g2s.trading.openman.OpenMan
import com.g2s.trading.order.Symbol
import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategySpecRepository
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Service

@Service
class DreamAndHope(
    private val mongoStrategySpecRepository: StrategySpecRepository,
    val openMans: List<OpenMan>,
    val closeMans: List<CloseMan>,
    private val scheduler: ThreadPoolTaskScheduler
) {


    fun test() {
        val strategyKey = "simple"
        val spec = mongoStrategySpecRepository.findStrategySpecByKey(strategyKey)
        val openMan = openMans.first { it.type() == spec.strategyType }
        val closeMan = closeMans.first { it.type() == spec.strategyType }

        val trigger = CronTrigger(spec.trigger)
        scheduler.schedule(
            { openMan.open(spec) },
            trigger
        )

        scheduler.schedule(
            { closeMan.close(spec) },
            CronTrigger("0/1 * * * * ?")
        )
    }

    fun dbTest() {
        // insert
        val simpleStrategySpec = StrategySpec(
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
        mongoStrategySpecRepository.saveStrategySpec(simpleStrategySpec)
        // read
        mongoStrategySpecRepository.findStrategySpecByKey(simpleStrategySpec.strategyKey)
    }
}
