package com.g2s.trading.order

enum class Symbol(val quantityPrecision: Int, val value: String, val minQtyMarket: Double, val minNotionalValue: Double) {
    BTCUSDT(3, "BTCUSDT", 0.001, 100.0),
    ETHUSDT(3, "ETHUSDT", 0.001, 20.0),
    BCHUSDT(3, "BCHUSDT", 0.001, 5.0)
}

