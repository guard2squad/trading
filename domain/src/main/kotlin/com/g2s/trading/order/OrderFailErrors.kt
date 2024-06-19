package com.g2s.trading.order

import com.g2s.trading.common.ApiError
import com.g2s.trading.common.ApiErrors
import org.slf4j.event.Level

enum class OrderFailErrors(val code: String) : ApiErrors {
    ORDER_IMMEDIATELY_TRIGGERED_ERROR("-2021") {
        override fun error(message: String, cause: Throwable, logLevel: Level, logMessage: String?): ApiError {
            return ApiError(
                code = this.code,
                message = message,
                cause = cause,
                logLevel = logLevel,
                logMessage = logMessage
            )
        }
    },
    MARKET_ORDER_REJECTED_ERROR("-4131") {
        override fun error(message: String, cause: Throwable, logLevel: Level, logMessage: String?): ApiError {
            return ApiError(
                code = this.code,
                message = message,
                cause = cause,
                logLevel = logLevel,
                logMessage = logMessage
            )
        }
    },
}
