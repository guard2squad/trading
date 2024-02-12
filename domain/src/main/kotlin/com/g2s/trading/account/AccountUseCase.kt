package com.g2s.trading.account

import com.g2s.trading.PositionEvent
import com.g2s.trading.UserEvent
import com.g2s.trading.exchange.Exchange
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean

@Service
class AccountUseCase(
    private val exchangeImpl: Exchange
) {
    lateinit var localAccount: Account
    private var synced = false
    private val lock = AtomicBoolean(false)

    @EventListener
    fun handleRefreshAccountEvent(event: UserEvent.AccountRefreshEvent) {
        this.localAccount = event.source
        synced = true
    }


    fun getAllocatedBalancePerStrategy(asset: Asset, availableRatio: Double): BigDecimal {
        val assetWallet = this.localAccount.assetWallets.first { it.asset == asset }
        return BigDecimal(assetWallet.walletBalance).multiply(BigDecimal(availableRatio))
    }

    fun getAvailableBalance(asset: Asset): BigDecimal {
        val assetWallet = localAccount.assetWallets.first { it.asset == asset }
        return BigDecimal(assetWallet.availableBalance)
    }


    fun setUnSynced() {
        synced = false
    }

    fun isSynced(): Boolean {
        return synced
    }

    fun acquire(): Boolean {
        return lock.compareAndSet(false, true)
    }

    fun release(): Boolean {
        return lock.compareAndSet(true, false)
    }
}
