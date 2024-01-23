package com.g2s.trading.account

import com.g2s.trading.Exchange
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class AccountUseCase(
    private val exchangeImpl: Exchange
) {
    lateinit var account: Account

    fun syncAccount() {
        val account = exchangeImpl.getAccount()
        this.account = account
    }

    @Synchronized
    fun getAccount(): Account {
        syncAccount()
        return account
    }

    fun getAllocatedBalancePerStrategy(asset: Asset, availableRatio : Double): BigDecimal {
        val assetWallet = account.assetWallets.first { it.asset == asset }
        return BigDecimal(assetWallet.walletBalance).multiply(BigDecimal(availableRatio))
    }

    fun getAvailableBalance(asset: Asset): BigDecimal {
        val assetWallet = account.assetWallets.first { it.asset == asset }
        return BigDecimal(assetWallet.availableBalance)
    }
}
