package com.g2s.trading.controller

import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.response.ApiResponse
import com.g2s.trading.response.ApiResponseService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/account")
class AccountController(
    private val apiResponseService: ApiResponseService,
    private val accountUseCase: AccountUseCase
) {
    @GetMapping
    fun get(): ApiResponse {
        val account = accountUseCase.getAccount()

        return apiResponseService.ok(account)
    }
}