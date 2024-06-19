package com.g2s.trading.account

import com.g2s.trading.exchange.Exchange
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
class AccountUseCase(
    private val exchangeImpl: Exchange
) {
    private lateinit var localAccount: Account
    private val lock = ReentrantReadWriteLock()

    init {
        localAccount = loadAccount()
    }

    fun withdraw(positionMargin: BigDecimal, commission: BigDecimal): Money {
        lock.writeLock().lock()
        try {
            localAccount.sync()
            if (positionMargin > localAccount.availableBalance) {
                return Money.NotAvailableMoney("예상 포지션 명목 가치 > 사용 가능 금액")
            }
            localAccount.transfer(positionMargin)
            if (commission > localAccount.availableBalance) {
                localAccount.transfer(-positionMargin)
                return Money.NotAvailableMoney("수수료 > 사용 가능 금액")
            }
            localAccount.withdraw(commission)

            return Money.AvailableMoney(positionMargin, commission)
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun undoWithdrawal(money: Money.AvailableMoney) {
        lock.writeLock().lock()
        try {
            localAccount.transfer(-money.positionMargin)
            localAccount.deposit(money.fee)
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun deposit(amount: BigDecimal) {
        lock.writeLock().lock()
        try {
            localAccount.deposit(amount)
        } finally {
            lock.writeLock().unlock()
        }
    }

    // unavailable -> available
    fun transferToAvailable(amount: BigDecimal) {
        lock.writeLock().lock()
        try {
            localAccount.transfer(-amount)
        } finally {
            lock.writeLock().unlock()
        }

    }

    fun getAccount(): Account {
        lock.readLock().lock()
        try {
            return localAccount
        } finally {
            lock.readLock().unlock()
        }
    }

//    // debug
//    fun printAccount() {
//        exchangeImpl.getAccount()
//    }

    private fun loadAccount(): Account {
        return exchangeImpl.getAccount()
    }
}