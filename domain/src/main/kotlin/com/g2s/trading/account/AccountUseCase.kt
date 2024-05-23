package com.g2s.trading.account

import com.g2s.trading.exchange.Exchange
import com.g2s.trading.strategy.StrategySpec
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class AccountUseCase(
    private val exchangeImpl: Exchange
) {
    private lateinit var localAccount: Account
    private val lock = AtomicBoolean(false)

    init {
        localAccount = loadAccount()
    }

    fun withdraw(spec: StrategySpec): Money {
        if (!acquire()) return Money.NotAvailableMoney  // TODO: 이름 변경
        localAccount.sync()

        val withdrawAmount = (localAccount.totalBalance * spec.allocatedRatio) / spec.symbols.size
        if (withdrawAmount <= localAccount.availableBalance) {
            localAccount.withdraw(withdrawAmount)
        }

        release()
        return Money.AvailableMoney(withdrawAmount)
    }

    fun withdraw(amount: Double): Money {
        if (!acquire()) return Money.NotAvailableMoney
        localAccount.sync()

        if (amount <= localAccount.availableBalance) {
            localAccount.withdraw(amount)
        }

        release()
        return Money.AvailableMoney(amount)
    }

    fun deposit(amount: Double) {
        localAccount.deposit(amount)
    }

    private fun acquire(): Boolean {
        return lock.compareAndSet(false, true)
    }

    private fun release(): Boolean {
        return lock.compareAndSet(true, false)
    }


    private fun loadAccount(): Account {
        return exchangeImpl.getAccount()
    }
}