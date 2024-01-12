package com.g2s.trading

import com.fasterxml.jackson.annotation.JsonProperty

data class Condition(
    @JsonProperty("unRealizedProfit") val unRealizedProfit: Double?,
    @JsonProperty("hammerRatio") val hammerRatio: Double?,
    @JsonProperty("positionAmt") val positionAmt: Long?,
)

// 조건에 필요한 항목 계속 추가
// unRealizedProfit: 미실현 손익 -> close 조건
// hammerRatio: high - low / close - open  -> open 조건
