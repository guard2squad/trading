package com.g2s.trading.account

import com.g2s.trading.exchange.Exchange
import com.g2s.trading.strategy.NewStrategySpec
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class NewAccountUseCase(
    private val exchangeImpl: Exchange
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private lateinit var localAccount: NewAccount
    private val lock = AtomicBoolean(false)

    init {
        localAccount = loadAccount()
    }

    fun withdraw(amount: Double) {
        localAccount.withdraw(amount)
    }

    fun withdraw(spec: NewStrategySpec): Money {
        if (!acquire()) return Money.NotAvailableMoney
        localAccount.sync()

        val withdrawAmount = (localAccount.totalBalance * spec.allocatedRatio) / spec.symbols.size
        if (withdrawAmount <= localAccount.availableBalance) {
            localAccount.withdraw(withdrawAmount)
        }

        release()
        return Money.AvailableMoney(withdrawAmount)
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


    private fun loadAccount(): NewAccount {
        return exchangeImpl.getAccount()
    }
}