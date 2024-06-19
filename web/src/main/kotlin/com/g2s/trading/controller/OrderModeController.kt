package com.g2s.trading.controller

import com.g2s.trading.response.ApiResponseService
import com.g2s.trading.response.ApiResult
import com.g2s.trading.strategy.Strategy
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orderMode")
class OrderModeController(
    applicationContext: ApplicationContext,
    private val apiResponseService: ApiResponseService
) {

    private val strategies: List<Strategy> = applicationContext.getBeansOfType(Strategy::class.java).values.toList()

    @PostMapping
    fun changeOrderMode(
        @RequestParam(name = "strategyType") strategyType: String,
        @RequestParam(name = "orderMode") orderMode: String
    ): HttpEntity<ApiResult> {
        strategies.first { it.getType().name == strategyType }.changeOrderMode(orderMode)

        return apiResponseService.noContent().toResponseEntity()
    }
}
