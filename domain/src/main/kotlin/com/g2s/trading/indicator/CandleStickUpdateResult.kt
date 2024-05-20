package com.g2s.trading.indicator

sealed class CandleStickUpdateResult {

    data class Success(
        val old: CandleStick,
        val new: CandleStick
    ): CandleStickUpdateResult()

    data object Failed : CandleStickUpdateResult()
}