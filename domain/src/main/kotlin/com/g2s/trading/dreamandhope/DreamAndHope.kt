package com.g2s.trading.dreamandhope

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.g2s.trading.closeman.CloseMan
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.indicator.IndicatorUseCase
import com.g2s.trading.indicator.indicator.Interval
import com.g2s.trading.openman.OpenMan
import com.g2s.trading.order.Symbol
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionUseCase
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
    private val positionUseCase: PositionUseCase,
    private val indicatorUseCase: IndicatorUseCase,
    openMans: List<OpenMan>,
    closeMans: List<CloseMan>,
) {
    private val openManMap = openMans.associateBy { it.type() }
    private val closeManMap = closeMans.associateBy { it.type() }
    private val openManFutureMap = mutableMapOf<String, ScheduledFuture<*>>()
    private val closeManFutureMap = mutableMapOf<String, ScheduledFuture<*>>()

    private val logger = LoggerFactory.getLogger(this.javaClass)

    fun init() {
        // 초기화 (spec 실행)
        try {
            // 시작하자 마자 바로 포지션 여는 경우 방지
            val symbols = enumValues<Symbol>()
            val specs = mongoStrategySpecRepository.findAllServiceStrategySpec()
            specs.forEach {
                try {
                    updateLastPositionForSpec(it, symbols)
                } catch (ignore: Exception) {
                    logger.info("update last candlestick for $it failed")
                }
            }
            specs.forEach {
                try {
                    start(it)
                } catch (ignore: Exception) {
                    // do nothing
                }
            }
        } catch (e: Exception) {
            throw DreamAndHopeErrors.INITIALIZE_ERROR.error()
        }
    }

    private fun start(spec: StrategySpec) {
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

    private fun updateLastPositionForSpec(spec: StrategySpec, symbols: Array<Symbol>) {
        symbols.forEach {
            val candleSticks = indicatorUseCase.getCandleStick(it, Interval.ONE_MINUTE, 2)
            val lastCandleStick = candleSticks.first()
            val dummyPosition = Position(it, 0.0, 0.0)
            dummyPosition.referenceData = ObjectMapperProvider.get().convertValue(lastCandleStick, JsonNode::class.java)
            positionUseCase.updateLastPosition(spec.strategyKey, dummyPosition)
        }
    }
}
