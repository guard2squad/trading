package com.g2s.trading

import com.fasterxml.jackson.annotation.JsonProperty

data class CandleStick(
    @JsonProperty("openTime") val openTime: Long,
    @JsonProperty("open") val open: Double,
    @JsonProperty("high") val high: Double,
    @JsonProperty("low") val low: Double,
    @JsonProperty("close") val close: Double,
    @JsonProperty("volume") val volume: Double,
    @JsonProperty("closeTime") val closeTime: Long,
    @JsonProperty("quoteAssetVolume") val quoteAssetVolume: Double,   // quoteAsset =  USDT
    @JsonProperty("numberOfTrades") val numberOfTrades: Int,    // 거래 횟수
    @JsonProperty("takerBuyBaseAssetVolume") val takerBuyBaseAssetVolume: Double,
    @JsonProperty("takerBuyQuoteAssetVolume") val takerBuyQuoteAssetVolume: Double
)