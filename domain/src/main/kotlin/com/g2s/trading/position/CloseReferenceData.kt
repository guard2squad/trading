package com.g2s.trading.position

sealed class CloseReferenceData {
    data class SimpleCloseReferenceData(val price: Double) : CloseReferenceData()
}
