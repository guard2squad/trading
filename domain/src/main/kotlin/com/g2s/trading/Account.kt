package com.g2s.trading

data class Account(
    val asset: String,
    val balance: Double,
    val crossWalletBalance: Double,
    val availableBalance: Double,
)