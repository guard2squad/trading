package com.g2s.trading.event

import com.g2s.trading.strategy.Strategy
import com.g2s.trading.strategy.StrategySpecUseCase
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import kotlin.reflect.KClass

@Service
class EventRouter(
    private val strategySpecUseCase: StrategySpecUseCase,
    applicationContext: ApplicationContext
) {
    // 이벤트 - 전략
    private val eventRoutingTable: Map<KClass<out Event>, MutableList<Strategy>>

    init {
        eventRoutingTable = applicationContext.getBeansOfType(Strategy::class.java).values
            .flatMap { strategy ->
                strategy.getTriggerEventTypes().map { event -> event to strategy }
            }.fold(mutableMapOf()) { acc, pair ->
                if (acc[pair.first] == null) {
                    acc[pair.first] = mutableListOf(pair.second)
                } else {
                    acc[pair.first]!!.add(pair.second)
                }

                acc
            }
    }

    @EventListener
    fun route(event: Event) {
        eventRoutingTable[event::class]?.let { strategies ->
            strategies.forEach { strategy ->
                strategySpecUseCase.findSpecsByStrategyType(strategy.getType())?.forEach { spec ->
                    strategy.handle(event, spec)
                }
            }
        }
    }
}