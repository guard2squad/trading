package com.g2s.trading.account

import com.g2s.trading.exchange.Exchange
import com.g2s.trading.strategy.StrategySpec
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean

@Service
class AccountUseCase(
    private val exchangeImpl: Exchange
) {
    private val logger = LoggerFactory.getLogger(AccountUseCase::class.java)
    private lateinit var localAccount: Account

    init {
        localAccount = loadAccount()
    }

    @Synchronized
    fun withdraw(spec: StrategySpec, commissionRate: BigDecimal): Money {
        localAccount.sync()
        // 포지션에 할당된 금액 이동
        val allocatedAmount =
            localAccount.totalBalance * BigDecimal(spec.allocatedRatio) / BigDecimal(spec.symbols.size)
        if (allocatedAmount > localAccount.availableBalance) {
            logger.info("allocatedAmount: $allocatedAmount > availableBalance: ${localAccount.availableBalance}")
            return Money.NotAvailableMoney
        }
        localAccount.transfer(allocatedAmount)
        // 예상 수수료 계산
        val expectedFee = allocatedAmount * commissionRate * BigDecimal(2)
        if (expectedFee > localAccount.availableBalance) {
            logger.info("expectedFee: $expectedFee > availableBalance: ${localAccount.availableBalance}")
            localAccount.transfer(-allocatedAmount)
            return Money.NotAvailableMoney
        }
        localAccount.withdraw(expectedFee)

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

    private fun loadAccount(): Account {
        return exchangeImpl.getAccount()
    }
}