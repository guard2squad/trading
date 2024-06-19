package com.g2s.trading.strategy

import com.g2s.trading.event.Event
import kotlin.reflect.KClass

interface Strategy {
    fun getType(): StrategyType
    fun getTriggerEventTypes(): List<KClass<out Event>>
    fun handle(event: Event, spec: StrategySpec)
    fun changeOrderMode(orderMode: String)
}
