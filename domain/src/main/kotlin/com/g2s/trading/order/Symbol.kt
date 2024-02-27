package com.g2s.trading.order

enum class Symbol(val precision: Int, val value: String, val minQtyMarket: Double) {
    BTCUSDT(3, "BTCUSDT", 0.001),
    ETHUSDT(3, "ETHUSDT", 0.001),
    BCHUSDT(3, "BCHUSDT", 0.001)
}

