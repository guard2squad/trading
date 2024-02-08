package com.g2s.trading.exchange

enum class BinanceStreamEventType(val value: String) {
    KLINE("kline"),
    MARK_PRICE("markPriceUpdate"),
    ACCOUNT_UPDATE("ACCOUNT_UPDATE");

    companion object {
        fun fromValue(value: String): BinanceStreamEventType {
            return entries.find { it.value == value }
                ?: throw IllegalArgumentException("No Enum constant matches: $value")
        }
    }
}
