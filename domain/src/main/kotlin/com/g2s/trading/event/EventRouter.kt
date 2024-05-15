package com.g2s.trading.event

import com.g2s.trading.strategy.NewStrategy
import com.g2s.trading.strategy.NewStrategySpecUseCase
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import kotlin.reflect.KClass

@Service
class EventRouter(
    private val newStrategySpecUseCase: NewStrategySpecUseCase,
    applicationContext: ApplicationContext
) {
    // 이벤트 - 전략
    private val eventRoutingTable: Map<KClass<out NewEvent>, MutableList<NewStrategy>>

    init {
        eventRoutingTable = applicationContext.getBeansOfType(NewStrategy::class.java).values
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
    fun route(event: NewEvent) {
        eventRoutingTable[event::class]?.let { strategies ->
            strategies.forEach { strategy ->
                newStrategySpecUseCase.findSpecsByStrategyType(strategy.getType())?.forEach { spec ->
                    strategy.handle(event, spec)
                }
            }
        }
    }
}