package com.g2s.trading.order

sealed class SendOrderResult {
    data object Success : SendOrderResult()

    data class Failure(
        val e: Exception
    ) : SendOrderResult()
}