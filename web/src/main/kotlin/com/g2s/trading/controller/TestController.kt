package com.g2s.trading.controller

import com.g2s.trading.TestUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class TestController(
    val testUseCase: TestUseCase
) {
    @GetMapping("/test")
    fun test() {
        testUseCase.getAllHistories()
    }
}
