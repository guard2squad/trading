package com.g2s.trading.event

import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.ApplicationEventMulticaster
import org.springframework.stereotype.Component

@Component
class EventUseCase(
    private val applicationEventMulticaster: ApplicationEventMulticaster,
) {
    fun publishAsyncEvent(vararg event: ApplicationEvent) {
        event.forEach {
            applicationEventMulticaster.multicastEvent(it)
        }
    }
}
