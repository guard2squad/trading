package com.g2s.trading.order

import com.g2s.trading.common.ApiError
import com.g2s.trading.common.ApiErrors
import org.slf4j.event.Level

enum class OrderFailErrors : ApiErrors {
    RETRYABLE_ERROR {
        override fun error(message: String, cause: Throwable, logLevel: Level, logMessage: String?): ApiError {
            return ApiError(
                code = "0",
                message = "retry order",
                logLevel = Level.INFO
            )
        }
    },
    IGNORABLE_ERROR {
        override fun error(message: String, cause: Throwable, logLevel: Level, logMessage: String?): ApiError {
            return ApiError(
                code = "1",
                message = "ignorable",
                logLevel = Level.INFO
            )
        }
    },
    UNKNOWN_ERROR {
        override fun error(message: String, cause: Throwable, logLevel: Level, logMessage: String?): ApiError {
            return ApiError(
                code = "-1",
                message = "UNKNOWN",
                logLevel = Level.ERROR
            )
        }
    },
}
