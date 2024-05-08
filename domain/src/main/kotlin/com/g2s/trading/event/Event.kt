package com.g2s.trading.event

import org.springframework.context.ApplicationEvent

sealed class Event(
    source: Any
): ApplicationEvent(source)