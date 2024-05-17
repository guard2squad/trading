package com.g2s.trading.controller

import com.g2s.trading.strategy.NewStrategy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orderMode")
class OrderModeController {

    @Autowired
    private lateinit var applicationContext: ApplicationContext
    private var strategies: List<NewStrategy>? = null
    private var isReedy = false

    @PostMapping
    fun changeOrderMode(
        @RequestParam(name = "strategyType") strategyType: String,
        @RequestParam(name = "orderMode") orderMode: String
    ) {
        if (!isReedy) {
            setUp()
        }
        strategies?.first { it.getType().name == strategyType }
        // TODO("CHANGE ORDER_MODE")
    }

    private fun setUp() {
        strategies = applicationContext.getBeansOfType(NewStrategy::class.java).values.toList()
        isReedy = true
    }
}
