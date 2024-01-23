package com.g2s.trading.account

import com.g2s.trading.position.Position

data class Account(
    val assetWallets: List<AssetWallet>,
    val positions: List<Position>
)
