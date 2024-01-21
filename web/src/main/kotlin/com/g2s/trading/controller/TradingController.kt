package com.g2s.trading.controller

import com.g2s.trading.DreamAndHope
import com.g2s.trading.PositionMode
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class TradingController(
    private val dreamAndHope: DreamAndHope
) {

    @GetMapping("/test")
    fun test() {
        dreamAndHope.test()
    }
}