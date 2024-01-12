package com.g2s.trading

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class Position(
    @JsonProperty("positionAmt") val positionAmt: Double,
    @JsonProperty("entryPrice") val entryPrice: Double,
    @JsonProperty("breakEvenPrice") val breakEvenPrice: Double,
    @JsonProperty("markPrice") val markPrice: Double,
    @JsonProperty("unRealizedProfit") val unRealizedProfit: Double,
    @JsonProperty("liquidationPrice") val liquidationPrice: Double,
    @JsonProperty("updateTime") val updateTime: Long,
)

/*
symbol	:	BTCUSDT
positionAmt	:	0.188
entryPrice	:	46540
breakEvenPrice	:	46549.308
markPrice	:	45998.16208089
unRealizedProfit	:	-101.86552879
liquidationPrice	:	31217.15019311
leverage	:	10
maxNotionalValue	:	40000000.00000000
marginType	:	isolated
isolatedMargin	:	2802.30553185
isAutoAddMargin	:	false
positionSide	:	BOTH
notional	:	8647.65447121
isolatedWallet	:	2904.17106064
updateTime	:	17050464004272024-01-12T08:00:00.427Z
isolated	:	true
adlQuantile	:	1
 */