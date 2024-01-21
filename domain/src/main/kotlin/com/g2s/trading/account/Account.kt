package com.g2s.trading.account

data class Account(
    val assetWallets: List<AssetWallet>,
    val positions: List<Position>
)
