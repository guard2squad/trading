package com.g2s.trading.dreamandhope

import com.g2s.trading.closeman.CloseMan
import com.g2s.trading.openman.OpenMan
import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategySpecRepository
import com.g2s.trading.strategy.StrategySpecServiceStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Service
import java.util.concurrent.ScheduledFuture

@Service
class DreamAndHope(
    private val mongoStrategySpecRepository: StrategySpecRepository,
    private val scheduler: ThreadPoolTaskScheduler,
    openMans: List<OpenMan>,
    closeMans: List<CloseMan>
) {
    private val openManMap = openMans.associateBy { it.type() }
    private val closeManMap = closeMans.associateBy { it.type() }
    private val openManFutureMap = mutableMapOf<String, ScheduledFuture<*>>()
    private val closeManFutureMap = mutableMapOf<String, ScheduledFuture<*>>()

    private val logger = LoggerFactory.getLogger(this.javaClass)

    fun init() {
        // 초기화 (spec 실행)
        try {
            val specs = mongoStrategySpecRepository.findAllServiceStrategySpec()
            specs.forEach { start(it) }
        } catch (e: Exception) {
            throw DreamAndHopeErrors.INITIALIZE_ERROR.error()
        }
    }

    private fun start(spec: StrategySpec) {
        try {
            // strategyType으로 openMan, closeMan 조회
            val openMan = openManMap[spec.strategyType] ?: return
            val closeMan = closeManMap[spec.strategyType] ?: return

            val trigger = CronTrigger(spec.trigger)

            // openMan 스케줄 등록
            scheduler.schedule(
                { openMan.open(spec) },
                trigger
            )?.let { future ->
                // openMan 스케줄 퓨처 저장 (중지시 필요)
                openManFutureMap[spec.strategyKey] = future
            }

            // closeMan 스케줄 등록
            scheduler.schedule(
                { closeMan.close(spec) },
                CronTrigger("0/1 * * * * ?")
            )?.let { future ->
                // closeMan 스케줄 퓨처 저장 (중지시 필요)
                closeManFutureMap[spec.strategyKey] = future
            }
        } catch (ignore: Exception) {
            // do nothing
        }
    }

    fun start(strategyKey: String) {
        try {
            logger.debug("start: $strategyKey")
            // strategyKey로 DB에서 스펙조회
            mongoStrategySpecRepository.findStrategySpecByKey(strategyKey)
                ?.let {
                    if (it.status == StrategySpecServiceStatus.SERVICE) start(it)
                }
        } catch (e: Exception) {
            throw DreamAndHopeErrors.START_STRATEGY_ERROR.error("Failed to start strategy: $strategyKey", e)
        }
    }

    fun stop(strategyKey: String) {
        try {
            logger.debug("stop: $strategyKey")
            mongoStrategySpecRepository.findStrategySpecByKey(strategyKey)
                ?.let {
                    if (it.status == StrategySpecServiceStatus.STOP) {
                        // openMan, closeMan future 캔슬 (interrupt 허용)
                        openManFutureMap[strategyKey]?.cancel(true)
                        closeManFutureMap[strategyKey]?.cancel(true)
                    }
                }
        } catch (e: Exception) {
            throw DreamAndHopeErrors.STOP_STRATEGY_ERROR.error("Failed to stop strategy: $strategyKey", e)
        }
    }
}
