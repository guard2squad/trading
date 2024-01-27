package com.g2s.trading.scheduler

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ScheduledThreadPoolExecutor

@Configuration
class SchedulerConfig {

    @Bean
    fun scheduler(): ThreadPoolTaskScheduler {
        return ThreadPoolTaskScheduler().apply {
            poolSize = 2
        }
    }
}
