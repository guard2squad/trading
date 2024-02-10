package com.g2s.trading.exchange

enum class BinanceStreamAbbreviatedKey(val value: String) {
    EVENT_TYPE("e"), // Event type is unique key. Ex) "e": "kline"
    EVENT_TIME("E"),
}
