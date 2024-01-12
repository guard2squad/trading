package com.g2s.trading

data class Indicator(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long,
    val quoteAssetVolume: Double,   // quoteAsset =  USDT
    val numberOfTrades: Int,    // 거래 횟수
    val takerBuyBaseAssetVolume: Double,
    val takerBuyQuoteAssetVolume: Double,
    val lastPrice: Double,
)