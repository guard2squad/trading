package com.g2s.trading

import com.g2s.trading.closeman.CloseMan
import com.g2s.trading.openman.OpenMan
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

    fun dbTest() : String {
        // read
        val res = mongoStrategySpecRepository.findStrategySpecByKey("simple")
        return "key : " + res.strategyKey + " " + "type : " + res.strategyType
    }
}
