package com.g2s.trading.strategy

import com.g2s.trading.event.NewEvent
import kotlin.reflect.KClass

interface NewStrategy {
    fun getType(): NewStrategyType
    fun getTriggerEventTypes(): List<KClass<out NewEvent>>
    fun handle(event: NewEvent, spec: NewStrategySpec)
}