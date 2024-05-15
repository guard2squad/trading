package com.g2s.trading.event

import com.g2s.trading.strategy.NewStrategySpec
import org.springframework.context.ApplicationEvent

sealed class StrategyEvent (
    source: Any
): ApplicationEvent(source){
    data class StartStrategyEvent(
        val source: NewStrategySpec
    ): StrategyEvent(source)

    data class StopStrategyEvent(
        val source: NewStrategySpec
    ): StrategyEvent(source)

    data class UpdateStrategyEvent(
        val source: NewStrategySpec
    ): StrategyEvent(source)
}
