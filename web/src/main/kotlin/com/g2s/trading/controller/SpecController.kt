package com.g2s.trading.controller

import com.g2s.trading.DreamAndHope
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SpecController(
    private val dreamAndHope: DreamAndHope
) {

    @GetMapping("/spec")
    fun dbTest() {
        dreamAndHope.dbTest()
    }
}
