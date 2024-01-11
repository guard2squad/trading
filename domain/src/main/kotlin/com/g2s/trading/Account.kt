package com.g2s.trading

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class Account(
    @JsonProperty("asset") val asset: String,
    @JsonProperty("balance") val balance: Double,
    @JsonProperty("crossWalletBalance") val crossWalletBalance: Double,
    @JsonProperty("availableBalance") val availableBalance: Double,
)