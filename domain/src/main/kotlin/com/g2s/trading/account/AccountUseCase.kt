package com.g2s.trading.account

import com.g2s.trading.exchange.Exchange
import com.g2s.trading.strategy.StrategySpec
import org.springframework.stereotype.Service
import java.math.BigDecimal
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

    fun withdraw(spec: StrategySpec, commissionRate: BigDecimal): Money {
        if (!acquire()) return Money.NotAvailableMoney
        localAccount.sync()
        // 포지션에 할당된 금액 이동
        val allocatedAmount =
            localAccount.totalBalance * BigDecimal(spec.allocatedRatio) / BigDecimal(spec.symbols.size)
        if (allocatedAmount > localAccount.availableBalance) {
            release()
            return Money.NotAvailableMoney
        }
        localAccount.transfer(allocatedAmount)
        // 예상 수수료 계산
        val expectedFee = allocatedAmount * commissionRate * BigDecimal(2)
        if (expectedFee > localAccount.availableBalance) {
            localAccount.transfer(-allocatedAmount)
            release()
            return Money.NotAvailableMoney
        }
        localAccount.withdraw(expectedFee)
        release()

        return Money.AvailableMoney(allocatedAmount, expectedFee)
    }

    fun cancelWithdrawal(money: Money.AvailableMoney) {
        localAccount.transfer(-money.allocatedAmount)
        localAccount.deposit(money.expectedFee)
    }

    fun deposit(amount: BigDecimal) {
        localAccount.deposit(amount)
    }

    // unavailable -> available
    fun reverseTransfer(amount: BigDecimal) {
        localAccount.transfer(-amount)
    }

    fun getAccount(): Account {
        return localAccount
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