package com.g2s.trading.event

import org.springframework.context.ApplicationEvent

sealed class NewEvent(
    source: Any
): ApplicationEvent(source)