package com.g2s.trading.position

sealed class LiquidationData {
    data class SimpleLiquidationData(val price: Double) : LiquidationData()
}
