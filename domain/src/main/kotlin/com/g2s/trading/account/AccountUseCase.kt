package com.g2s.trading.account

import com.g2s.trading.Exchange
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class AccountUseCase(
    private val exchangeImpl: Exchange
) {
    lateinit var localAccount: Account

    fun syncAccount() {
        this.localAccount = exchangeImpl.getAccount()
    }

    fun getAllocatedBalancePerStrategy(asset: Asset, availableRatio: Double): BigDecimal {
        val assetWallet = this.localAccount.assetWallets.first { it.asset == asset }
        return BigDecimal(assetWallet.walletBalance).multiply(BigDecimal(availableRatio))
    }

    fun getAvailableBalance(asset: Asset): BigDecimal {
        val assetWallet = localAccount.assetWallets.first { it.asset == asset }
        return BigDecimal(assetWallet.availableBalance)
    }
}
